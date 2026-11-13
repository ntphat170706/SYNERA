import test from 'node:test';
import assert from 'node:assert/strict';
import { noticeSummary } from '../src/domain/notifications.ts';
test('sales approval message is Vietnamese, not an event enum', () => {
  const n = noticeSummary({
    id: 1,
    type: 'SalesOrderUpdated',
    payload: '{"status":"NPP_CONFIRMED","code":"SA-7"}',
  });
  assert.equal(n.title, 'Đơn hàng đã được NPP xác nhận');
  assert.equal(n.description, 'Đơn SA-7 · NPP đã xác nhận');
});
