import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const { allowsMobileRequest } = createRequire(import.meta.url)('../scripts/mobile-policy.cjs');
test('Metro and Internet policy accepts the full native follow-up paths and rejects management/session access', () => {
  for (const path of [
    '/api/v1/sales-replacements',
    '/api/v1/sales-replacements/12/customer-feedback',
    '/api/v1/notifications/12/read?source=inbox',
  ])
    assert.equal(allowsMobileRequest('POST', path), true, path);
  for (const [method, path] of [
    ['POST', '/sales-replacements'],
    ['POST', '/api/v1/auth/session/login'],
    ['POST', '/api/v1/sales-replacements/12/approve'],
    ['GET', '/api/v1/users'],
    ['DELETE', '/api/v1/sales-orders/12'],
  ])
    assert.equal(allowsMobileRequest(method, path), false, path);
});
