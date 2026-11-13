import { describe, it, expect } from 'vitest';
import { notificationInfo } from './notifications';
describe('Vietnamese notification routing', () => {
  it('names confirmation and keeps the sales order link', () => {
    const n = notificationInfo({
      type: 'SalesOrderUpdated',
      payload: JSON.stringify({
        orderId: 7,
        channel: 'SELL_OUT',
        code: 'SA-7',
        status: 'NPP_CONFIRMED',
      }),
    });
    expect(n.title).toBe('Đơn hàng đã được NPP xác nhận');
    expect(n.description).not.toContain('NPP_CONFIRMED');
    expect(n.path).toBe('/orders?channel=sales&order=7');
  });
  it('does not expose raw event/status codes in older notices', () => {
    const n = notificationInfo({ type: 'OrderStatusChanged', payload: '{"status":"DELIVERED"}' });
    expect(n.title).not.toBe('OrderStatusChanged');
    expect(n.description).toBe('Đã giao');
  });
});
