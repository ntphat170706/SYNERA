import type { RecordData } from './api';
import { statusLabels } from './utils';
export function notificationInfo(n: RecordData) {
  let payload: RecordData = {};
  try {
    payload =
      typeof n.payload === 'string' ? JSON.parse(n.payload) : (n.payload as RecordData) || {};
  } catch {
    /* Legacy notifications may have a plain-text payload. */
  }
  const type = String(n.type),
    order = Number(payload?.orderId);
  const labels: Record<string, string> = {
    OrderCreated: 'Có đơn hàng mới',
    SalesOrderUpdated: 'Tiến độ đơn bán đã thay đổi',
    ReplacementProposed: 'Có đề xuất giao bù',
    ReplacementUpdated: 'Giao bù đã cập nhật',
    ReplacementCustomerUpdated: 'Sales đã báo lại khách',
    DeliveryShort: 'Giao hàng có chênh lệch',
    ReceiptReminder: 'Cần xác nhận nhận hàng',
    OrderStatusChanged: 'Trạng thái đơn hàng đã cập nhật',
    APPOINTMENT_DUE: 'Đến giờ hẹn với khách',
    PaymentConfirmed: 'Thanh toán đã được xác nhận',
    ReturnPosted: 'Phiếu trả hàng đã ghi nhận',
  };
  return {
    title:
      type === 'SalesOrderUpdated' && payload?.status === 'NPP_CONFIRMED'
        ? 'Đơn hàng đã được NPP xác nhận'
        : labels[type] || 'Cập nhật công việc',
    description:
      [
        payload?.code ? `Đơn ${payload.code}` : '',
        payload?.status ? statusLabels[String(payload.status)] || 'Đang cập nhật' : '',
      ]
        .filter(Boolean)
        .join(' · ') ||
      (type === 'APPOINTMENT_DUE' && typeof n.payload === 'string'
        ? n.payload
        : 'Có cập nhật mới liên quan đến công việc của bạn.'),
    path: type.startsWith('Replacement')
      ? Number.isSafeInteger(Number(payload?.replacementId)) && Number(payload?.replacementId) > 0
        ? `/npp-workflow?replacement=${payload.replacementId}`
        : '/npp-workflow'
      : Number.isSafeInteger(order) && order > 0
        ? `/orders?channel=${payload.channel === 'SELL_OUT' ? 'sales' : 'sellin'}&order=${order}`
        : type === 'DeliveryShort'
          ? '/tickets'
          : '',
  };
}
export function notificationTime(value: unknown) {
  const date = new Date(String(value)),
    diff = Date.now() - date.getTime();
  if (!Number.isFinite(diff)) return '';
  const minutes = Math.max(0, Math.floor(diff / 60000));
  return minutes < 1
    ? 'Vừa xong'
    : minutes < 60
      ? `${minutes} phút trước`
      : minutes < 1440
        ? `${Math.floor(minutes / 60)} giờ trước`
        : date.toLocaleString('vi-VN');
}
