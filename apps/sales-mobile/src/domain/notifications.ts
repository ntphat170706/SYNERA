import { statusLabel } from '../theme.ts';
export type SalesNotice = {
  id: number;
  type?: string;
  payload?: string;
  read?: boolean;
  createdAt?: string;
};
export function noticeSummary(n: SalesNotice) {
  let p: { status?: string; code?: string; orderId?: number } = {};
  try {
    const parsed = JSON.parse(n.payload || '{}');
    p = parsed && typeof parsed === 'object' ? parsed : {};
  } catch {}
  const type = n.type || '';
  const title =
    type === 'SalesOrderUpdated' && p.status === 'NPP_CONFIRMED'
      ? 'Đơn hàng đã được NPP xác nhận'
      : (
          {
            OrderCreated: 'Đơn hàng đã được gửi',
            SalesOrderUpdated: 'NPP cập nhật đơn hàng',
            OrderStatusChanged: 'Đơn hàng đã cập nhật',
            ReplacementProposed: 'Có đề xuất giao bù',
            ReplacementUpdated: 'Tiến độ giao bù đã cập nhật',
            ReplacementCustomerUpdated: 'Đã ghi kết quả báo khách',
            DeliveryShort: 'Đơn hàng có giao thiếu',
            APPOINTMENT_DUE: 'Đến giờ hẹn với khách',
            ReceiptReminder: 'Cần xác nhận thực nhận',
          } as Record<string, string>
        )[type] || 'Cập nhật công việc';
  return {
    title,
    description: [
      p.code ? `Đơn ${p.code}` : p.orderId ? `Đơn ${p.orderId}` : '',
      p.status ? statusLabel(p.status) : '',
    ]
      .filter(Boolean)
      .join(' · '),
  };
}
