import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';
const { validateBuildApi } = createRequire(import.meta.url)('../scripts/build-api.cjs');

test('IPA requires an explicit device-reachable API origin', () => {
  for (const value of [
    undefined,
    '',
    'http://127.0.0.1:8080',
    'http://localhost',
    'https://api.example.com/api/v1',
    'https://user:pass@api.example.com',
    'https://api.example.com?token=x',
  ]) {
    assert.throws(() => validateBuildApi(value, 'preview'));
  }
});
test('only internal preview permits private LAN HTTP', () => {
  assert.equal(
    validateBuildApi('http://172.30.84.160:5173', 'unsigned'),
    'http://172.30.84.160:5173',
  );
  assert.equal(
    validateBuildApi('http://172.30.84.160:5173', 'preview'),
    'http://172.30.84.160:5173',
  );
  assert.throws(() => validateBuildApi('http://172.30.84.160:5173', 'production'));
  assert.throws(() => validateBuildApi('http://api.example.com', 'preview'));
  assert.equal(
    validateBuildApi('https://api.example.com', 'production'),
    'https://api.example.com',
  );
});
