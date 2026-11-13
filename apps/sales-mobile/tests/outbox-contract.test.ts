import test from 'node:test';
import assert from 'node:assert/strict';
import { acknowledgedIds, type OwnedCommand, type SalesUser } from '../src/domain/contracts.ts';
const user: SalesUser = {
  id: 3,
  name: 'Test',
  role: 'SALES',
  nppId: 2,
  workspaceKey: 'npp:2',
  authorities: [],
};
const command = (clientId: string, ownerId = 3, workspaceKey = 'npp:2'): OwnedCommand => ({
  clientId,
  ownerId,
  workspaceKey,
  idempotencyKey: 'test-' + clientId,
  createdAt: '2026-11-11T01:00:00Z',
  status: 'LOCAL',
  payload: {},
});
test('Only an owned successful per-item ACK with a server order id can retire a native draft', () => {
  const drafts = [
    command('created'),
    command('duplicate'),
    command('changed'),
    command('rejected'),
    command('unknown'),
    command('foreign', 4),
    command('wrong-workspace', 3, 'npp:3'),
    command('missing-id'),
  ];
  const replies = [
    { clientId: 'created', status: 'CREATED', orderId: 11 },
    { clientId: 'duplicate', status: 'DUPLICATE', orderId: 12 },
    { clientId: 'changed', status: 'PRICE_CHANGED' },
    { clientId: 'rejected', status: 'REJECTED' },
    { clientId: 'unknown', status: 'UNKNOWN' },
    { clientId: 'foreign', status: 'CREATED', orderId: 13 },
    { clientId: 'wrong-workspace', status: 'CREATED', orderId: 14 },
    { clientId: 'missing-id', status: 'CREATED' },
    { clientId: 'not-sent', status: 'CREATED', orderId: 15 },
  ];
  assert.deepEqual(acknowledgedIds(drafts, replies, user), ['created', 'duplicate']);
  assert.equal(drafts.length, 8);
  assert.ok(drafts.every((c) => c.status === 'LOCAL'));
});
