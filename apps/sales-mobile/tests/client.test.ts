import test from 'node:test';
import assert from 'node:assert/strict';
import { createSalesClient, ApiFailure } from '../src/api/client.ts';
import type { Session } from '../src/domain/contracts.ts';
const session = (access = 'access-a', refresh = 'refresh-a', role = 'SALES'): Session => ({
  accessToken: access,
  refreshToken: refresh,
  user: {
    id: 3,
    name: 'Test Sales',
    role,
    nppId: 2,
    workspaceKey: 'npp:2',
    authorities: ['SALES_CREATE'],
  },
});
const ok = (data: unknown) =>
  new Response(JSON.stringify({ success: true, data }), { status: 200 });
const denied = (status = 401) =>
  new Response(
    JSON.stringify({
      success: false,
      error: { code: 'UNAUTHENTICATED', message: 'Phiên không hợp lệ' },
    }),
    { status },
  );
function vault() {
  let value: string | null = null;
  return {
    read: async () => value,
    write: async (v: string | null) => {
      value = v;
    },
  };
}
function fake(
  handler: (url: string, init: RequestInit) => Promise<Response> | Response,
): typeof fetch {
  return ((url, init) => handler(String(url), init || {})) as typeof fetch;
}
test('Only a Sales session is accepted and only its refresh token is persisted', async () => {
  const storage = vault();
  const client = createSalesClient(
    'http://local',
    storage,
    fake(() => ok(session())),
  );
  await client.login('test@example.local', 'test-password');
  assert.equal(client.user()?.id, 3);
  assert.equal(await storage.read(), 'refresh-a');
  const blocked = createSalesClient(
    'http://local',
    storage,
    fake(() => ok(session('admin-access', 'admin-refresh', 'SYSTEM_ADMIN'))),
  );
  await assert.rejects(
    blocked.login('admin@example.local', 'test-password'),
    (e: unknown) => e instanceof ApiFailure && e.code === 'SALES_ONLY',
  );
  assert.equal(await storage.read(), null);
  assert.equal(blocked.user(), null);
});
test('Concurrent expired reads share one refresh and do not send expired bearer to refresh', async () => {
  let refreshes = 0;
  const client = createSalesClient(
    'http://local',
    vault(),
    fake((url, init) => {
      if (url.endsWith('/auth/login')) return ok(session());
      if (url.endsWith('/auth/refresh')) {
        refreshes++;
        assert.equal((init.headers as Record<string, string>).Authorization, undefined);
        return ok(session('access-b', 'refresh-b'));
      }
      return (init.headers as Record<string, string>).Authorization === 'Bearer access-a'
        ? denied()
        : ok(['scoped']);
    }),
  );
  await client.login('test@example.local', 'test-password');
  assert.deepEqual(await Promise.all([client.get('/stores'), client.get('/visits/today')]), [
    ['scoped'],
    ['scoped'],
  ]);
  assert.equal(refreshes, 1);
});

test('Image upload sends FormData directly and lets fetch provide its multipart boundary', async () => {
  let sent: RequestInit | undefined;
  const client = createSalesClient(
    'http://local',
    vault(),
    fake((url, init) => {
      if (url.endsWith('/auth/login')) return ok(session());
      sent = init;
      return ok({ id: 25 });
    }),
  );
  await client.login('test@example.local', 'test-password');
  const form = new FormData();
  form.append('file', new Blob(['fixture'], { type: 'image/png' }), 'fixture.png');
  await client.post('/attachments/upload?nppId=2', form, 'upload-command-1');
  assert.equal(sent?.body, form);
  assert.equal(new Headers(sent?.headers).get('Content-Type'), null);
  assert.equal(new Headers(sent?.headers).get('Authorization'), 'Bearer access-a');
});

test('An expired mutation refreshes once and retries exactly the same body and idempotency key', async () => {
  const calls: { body: unknown; key: string | null }[] = [];
  const client = createSalesClient(
    'http://local',
    vault(),
    fake((url, init) => {
      if (url.endsWith('/auth/login')) return ok(session());
      if (url.endsWith('/auth/refresh')) return ok(session('access-new', 'refresh-new'));
      calls.push({
        body: JSON.parse(String(init.body)),
        key: new Headers(init.headers).get('Idempotency-Key'),
      });
      return new Headers(init.headers).get('Authorization') === 'Bearer access-a'
        ? denied()
        : ok({ id: 15 });
    }),
  );
  await client.login('test@example.local', 'test-password');
  const body = { storeId: 1, lines: [{ skuId: 2, uom: 'CHAI', qty: 3 }] };
  assert.deepEqual(await client.post('/sales-orders/sync', body, 'idempotent-command-1'), {
    id: 15,
  });
  assert.equal(calls.length, 2);
  assert.deepEqual(calls[0], calls[1]);
  assert.equal(calls[0].key, 'idempotent-command-1');
  assert.throws(
    () => client.post('/sales-orders/sync', body, ''),
    (e: unknown) => e instanceof ApiFailure && e.code === 'MISSING_IDEMPOTENCY',
  );
});
test('A refresh that finishes after logout cannot restore the session or token', async () => {
  let release!: (r: Response) => void;
  const waiting = new Promise<Response>((r) => {
    release = r;
  });
  let started!: () => void;
  const refreshStarted = new Promise<void>((r) => {
    started = r;
  });
  const storage = vault();
  const client = createSalesClient(
    'http://local',
    storage,
    fake((url) => {
      if (url.endsWith('/auth/login')) return ok(session());
      if (url.endsWith('/auth/refresh')) {
        started();
        return waiting;
      }
      if (url.endsWith('/auth/logout')) return ok({ revoked: true });
      return denied();
    }),
  );
  await client.login('test@example.local', 'test-password');
  const pending = client.get('/stores');
  const checked = assert.rejects(
    pending,
    (e: unknown) => e instanceof ApiFailure && e.code === 'STALE_SESSION',
  );
  await refreshStarted;
  await client.logout();
  release(ok(session('late', 'late-refresh')));
  await checked;
  assert.equal(client.user(), null);
  assert.equal(await storage.read(), null);
});
test('Late OTP verification does not sign the user back in after logout', async () => {
  let release!: (r: Response) => void;
  const waiting = new Promise<Response>((r) => {
    release = r;
  });
  const storage = vault();
  const client = createSalesClient(
    'http://local',
    storage,
    fake((url) =>
      url.endsWith('/auth/login')
        ? ok({ requiresOtp: true, challengeId: 7 })
        : url.endsWith('/auth/otp/verify')
          ? waiting
          : ok({}),
    ),
  );
  await client.login('test@example.local', 'test-password');
  const pending = client.verify(7, '123456');
  const checked = assert.rejects(
    pending,
    (e: unknown) => e instanceof ApiFailure && e.code === 'STALE_SESSION',
  );
  await client.logout();
  release(ok(session()));
  await checked;
  assert.equal(client.user(), null);
  assert.equal(await storage.read(), null);
});
test('Logout clears the device token even if the server cannot be reached', async () => {
  const storage = vault();
  const client = createSalesClient(
    'http://local',
    storage,
    fake((url) => {
      if (url.endsWith('/auth/logout')) throw Error('offline');
      return ok(session());
    }),
  );
  await client.login('test@example.local', 'test-password');
  await client.logout();
  assert.equal(client.user(), null);
  assert.equal(await storage.read(), null);
});
test('Revoked refresh clears the stored identity rather than fabricating a successful read', async () => {
  const storage = vault();
  let revoked = false;
  const client = createSalesClient(
    'http://local',
    storage,
    fake((url) => (url.endsWith('/auth/login') ? ok(session()) : revoked ? denied(403) : ok([]))),
  );
  await client.login('test@example.local', 'test-password');
  revoked = true;
  const restored = createSalesClient(
    'http://local',
    storage,
    fake(() => denied(403)),
  );
  await assert.rejects(restored.restore());
  assert.equal(await storage.read(), null);
  assert.equal(restored.user(), null);
});
