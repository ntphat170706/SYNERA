import type { RecordData } from './api';
export function orderInQueue(order: RecordData, queue: string, channel: string, role: string) {
  const status = String(order.status);
  if (queue === 'short')
    return (
      ['DELIVERED_SHORT', 'RECEIVED_DISCREPANCY'].includes(status) &&
      order.hasOutstandingShortage !== false
    );
  if (queue === 'receiving') return channel === 'sellin' && status === 'DELIVERED';
  if (queue !== 'pending') return true;
  if (role !== 'npp') return ['SUBMITTED', 'PENDING_APPROVAL', 'ON_HOLD_CREDIT'].includes(status);
  return (
    channel === 'sales'
      ? ['SUBMITTED', 'NPP_CONFIRMED', 'PICKING', 'DELIVERING']
      : ['DRAFT', 'NEED_INFO']
  ).includes(status);
}
