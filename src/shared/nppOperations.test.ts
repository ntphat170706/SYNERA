import { describe, it, expect } from 'vitest';
import { orderInQueue } from './nppOperations';
import { notificationInfo } from './notifications';
describe('NPP work queues and navigation', () => {
  it('distinguishes NPP actions from NSX approvals and does not leave fulfilled shortages open', () => {
    expect(orderInQueue({ status: 'PENDING_APPROVAL' }, 'pending', 'sellin', 'npp')).toBe(false);
    expect(orderInQueue({ status: 'PENDING_APPROVAL' }, 'pending', 'sellin', 'nsx')).toBe(true);
    expect(orderInQueue({ status: 'NEED_INFO' }, 'pending', 'sellin', 'npp')).toBe(true);
    expect(orderInQueue({ status: 'SUBMITTED' }, 'pending', 'sales', 'npp')).toBe(true);
    expect(
      orderInQueue(
        { status: 'DELIVERED_SHORT', hasOutstandingShortage: false },
        'short',
        'sales',
        'npp',
      ),
    ).toBe(false);
    expect(orderInQueue({ status: 'DELIVERED' }, 'receiving', 'sellin', 'npp')).toBe(true);
  });
  it('opens the correct channel from notification metadata and tolerates old payloads', () => {
    expect(
      notificationInfo({
        type: 'OrderCreated',
        payload: JSON.stringify({ orderId: 42, channel: 'SELL_OUT' }),
      }).path,
    ).toBe('/orders?channel=sales&order=42');
    expect(notificationInfo({ type: 'OrderCreated', payload: 'old text' }).path).toBe('');
    expect(
      notificationInfo({
        type: 'OrderCreated',
        payload: JSON.stringify({ orderId: 'https://external.invalid' }),
      }).path,
    ).toBe('');
  });
});
