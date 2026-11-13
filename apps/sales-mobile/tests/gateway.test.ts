import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createRequire } from 'node:module';
const { createGateway } = createRequire(import.meta.url)('../scripts/mobile-gateway.cjs');
const listen = (server: http.Server): Promise<number> =>
  new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve((server.address() as { port: number }).port));
  });

test('Internet gateway excludes web, browser sessions, management writes and health details', async () => {
  const gateway = createGateway();
  const port = await listen(gateway);
  try {
    for (const [path, method] of [
      ['/phone', 'GET'],
      ['/actuator/health', 'GET'],
      ['/api/v1/auth/session/login', 'POST'],
      ['/api/v1/sales-orders/1/approve', 'POST'],
      ['/api/v1/users', 'GET'],
    ]) {
      assert.ok(
        [404, 405].includes((await fetch(`http://127.0.0.1:${port}${path}`, { method })).status),
      );
    }
    const health = await fetch(`http://127.0.0.1:${port}/health`);
    assert.deepEqual(await health.json(), { service: 'SYNERA Sales API', ready: true });
  } finally {
    gateway.closeAllConnections();
    gateway.close();
  }
});

test('Internet gateway keeps token, key, multipart body and query while removing browser cookies', async () => {
  let received:
    { headers: http.IncomingHttpHeaders; url: string | undefined; body: string } | undefined;
  const backend = http.createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      received = { headers: req.headers, url: req.url, body };
      res.writeHead(201, {
        'Content-Type': 'application/json',
        'Set-Cookie': 'private=must-not-forward',
      });
      res.end(JSON.stringify({ success: true, data: { id: 1 } }));
    });
  });
  const backendPort = await listen(backend),
    gateway = createGateway({ backendPort });
  const port = await listen(gateway);
  try {
    const response = await fetch(`http://127.0.0.1:${port}/api/v1/attachments/upload?nppId=1`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer test-token',
        'Idempotency-Key': 'same-key',
        Cookie: 'browser=secret',
        Origin: 'https://browser.example',
        'Content-Type': 'multipart/form-data; boundary=test',
      },
      body: '--test\r\nunchanged upload\r\n--test--',
    });
    assert.equal(response.status, 201);
    assert.equal(response.headers.get('set-cookie'), null);
    assert.equal(received?.url, '/api/v1/attachments/upload?nppId=1');
    assert.equal(received?.headers.authorization, 'Bearer test-token');
    assert.equal(received?.headers['idempotency-key'], 'same-key');
    assert.equal(received?.headers.cookie, undefined);
    assert.equal(received?.headers.origin, undefined);
    assert.equal(received?.headers['content-type'], 'multipart/form-data; boundary=test');
    assert.equal(received?.body, '--test\r\nunchanged upload\r\n--test--');
  } finally {
    gateway.closeAllConnections();
    gateway.close();
    backend.closeAllConnections();
    backend.close();
  }
});
