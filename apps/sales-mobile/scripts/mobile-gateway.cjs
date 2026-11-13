const http = require('node:http');
const { allowsMobileRequest } = require('./mobile-policy.cjs');
function createGateway({ backendPort = 8080 } = {}) {
  return http.createServer((req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    const fail = (status, code) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: { code, message: code } }));
    };
    if (req.method === 'GET' && ['/', '/health', '/health/'].includes(req.url)) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ service: 'SYNERA Sales API', ready: true }));
    }
    const pathname = req.url?.split('?')[0] || '';
    if (!pathname.startsWith('/api/v1/')) return fail(404, 'NOT_FOUND');
    if (!allowsMobileRequest(req.method, req.url)) return fail(405, 'MOBILE_OPERATION_UNAVAILABLE');
    const headers = { ...req.headers, host: `127.0.0.1:${backendPort}` };
    for (const name of [
      'cookie',
      'origin',
      'referer',
      'connection',
      'forwarded',
      'x-forwarded-host',
    ])
      delete headers[name];
    const upstream = http.request(
      { hostname: '127.0.0.1', port: backendPort, path: req.url, method: req.method, headers },
      (reply) => {
        const responseHeaders = { ...reply.headers, 'cache-control': 'no-store' };
        delete responseHeaders['set-cookie'];
        delete responseHeaders.connection;
        res.writeHead(reply.statusCode || 502, responseHeaders);
        reply.pipe(res);
      },
    );
    upstream.setTimeout(25000, () => upstream.destroy());
    upstream.on('error', () => {
      if (!res.headersSent) fail(502, 'BACKEND_UNAVAILABLE');
      else res.destroy();
    });
    req.on('aborted', () => upstream.destroy());
    req.pipe(upstream);
  });
}
module.exports = { createGateway };
if (require.main === module) {
  createGateway().listen(8787, '127.0.0.1', () => {
    console.log('SYNERA mobile API gateway: http://127.0.0.1:8787');
  });
}
