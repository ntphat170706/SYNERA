import { afterEach, expect, it, vi } from 'vitest';
afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
});
it('refreshes an expired access token through the cookie endpoint without sending the expired bearer', async () => {
  vi.stubGlobal('navigator', { onLine: true });
  vi.stubGlobal('window', { dispatchEvent: vi.fn() });
  const user = {
    id: 2,
    name: 'NPP',
    role: 'NPP_OWNER',
    nppId: 2,
    workspaceKey: 'npp-2',
    authorities: [],
  };
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ success: false, error: { code: 'UNAUTHENTICATED' } }), {
        status: 401,
      }),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: { accessToken: 'renewed', user } }), {
        status: 200,
      }),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ success: true, data: [{ id: 9 }] }), { status: 200 }),
    );
  vi.stubGlobal('fetch', fetch);
  const client = await import('./api');
  client.acceptSession({ accessToken: 'expired', user });
  await expect(client.api('/sell-in-orders')).resolves.toEqual([{ id: 9 }]);
  expect(fetch.mock.calls[1][0]).toContain('/auth/session/refresh');
  expect(fetch.mock.calls[1][1].headers.Authorization).toBeUndefined();
  expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer renewed');
});
it('reuses an idempotency key after a lost response, then allows a new identical order after success', async () => {
  vi.stubGlobal('navigator', { onLine: true });
  vi.stubGlobal('window', { dispatchEvent: vi.fn() });
  const fetch = vi
    .fn()
    .mockRejectedValueOnce(new TypeError('Connection reset'))
    .mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: { id: 41 } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  vi.stubGlobal('fetch', fetch);
  const client = await import('./api');
  client.acceptSession({
    accessToken: 'test-token',
    user: {
      id: 1,
      name: 'NPP',
      role: 'NPP_OWNER',
      nppId: 2,
      workspaceKey: 'npp-2',
      authorities: [],
    },
  });
  await expect(client.command('/sell-in-orders', { nppId: 2 })).rejects.toThrow('Không kết nối');
  await expect(client.command('/sell-in-orders', { nppId: 2 })).resolves.toEqual({ id: 41 });
  const first = fetch.mock.calls[0][1].headers['Idempotency-Key'];
  expect(fetch.mock.calls[1][1].headers['Idempotency-Key']).toBe(first);
  fetch.mockResolvedValueOnce(
    new Response(JSON.stringify({ success: true, data: { id: 42 } }), { status: 200 }),
  );
  await client.command('/sell-in-orders', { nppId: 2 });
  expect(fetch.mock.calls[2][1].headers['Idempotency-Key']).not.toBe(first);
});
