import test from 'node:test';
import assert from 'node:assert/strict';
import { createSalesClient, ApiFailure } from '../src/api/client.ts';
const identity = {
  accessToken: 'access-test',
  refreshToken: 'refresh-test',
  user: { id: 3, name: 'Test', role: 'SALES', nppId: 2, workspaceKey: 'npp:2', authorities: [] },
};
test('A failed secure-store deletion is reported and logout can be retried without silently restoring another identity', async () => {
  let stored: string | null = null,
    fail = false,
    logouts = 0;
  const storage = {
    read: async () => stored,
    write: async (v: string | null) => {
      if (fail && v === null) throw Error('locked');
      stored = v;
    },
  };
  const transport = ((url: unknown) => {
    if (String(url).endsWith('/auth/logout')) logouts++;
    return Promise.resolve(
      new Response(JSON.stringify({ success: true, data: identity }), { status: 200 }),
    );
  }) as typeof fetch;
  const client = createSalesClient('http://local', storage, transport);
  await client.login('test@example.local', 'test-password');
  fail = true;
  await assert.rejects(
    client.logout(),
    (e: unknown) => e instanceof ApiFailure && e.code === 'STORAGE_ERROR',
  );
  assert.equal(logouts, 0);
  assert.equal(client.user()?.id, 3);
  fail = false;
  await client.logout();
  assert.equal(stored, null);
  assert.equal(client.user(), null);
  assert.equal(logouts, 1);
});
test('New reads cannot refresh credentials while secure logout is in progress', async () => {
  let release!: () => void,
    started!: () => void,
    armed = false,
    stored: string | null = null;
  const deleting = new Promise<void>((r) => {
      release = r;
    }),
    began = new Promise<void>((r) => {
      started = r;
    });
  const storage = {
    read: async () => stored,
    write: async (v: string | null) => {
      if (armed && v === null) {
        started();
        await deleting;
      }
      stored = v;
    },
  };
  let reads = 0;
  const transport = ((url: unknown) => {
    if (String(url).endsWith('/stores')) reads++;
    return Promise.resolve(
      new Response(JSON.stringify({ success: true, data: identity }), { status: 200 }),
    );
  }) as typeof fetch;
  const client = createSalesClient('http://local', storage, transport);
  await client.login('test@example.local', 'test-password');
  armed = true;
  const pending = client.logout();
  await began;
  await assert.rejects(
    client.get('/stores'),
    (e: unknown) => e instanceof ApiFailure && e.code === 'STALE_SESSION',
  );
  assert.equal(reads, 0);
  release();
  await pending;
  assert.equal(stored, null);
});
