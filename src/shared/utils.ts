import type { DB, Order, OrderLine, Status } from './types';
export const money = (n: number) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(n);
export const compact = (n: number) =>
  n >= 1e9
    ? `${(n / 1e9).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} tỷ`
    : n >= 1e6
      ? `${(n / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} tr`
      : n.toLocaleString('vi-VN');
export const total = (lines: OrderLine[]) => lines.reduce((s, l) => s + l.qty * l.price, 0);
export const now = () => new Date().toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
export const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
export const statusLabels: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  NOT_CONTACTED: 'Chưa liên hệ',
  CONTACTED: 'Đã liên hệ',
  CONSULTING: 'Đang tư vấn',
  WON: 'Đã chuyển đổi',
  LOST: 'Chưa thành công',
  PROPOSED: 'Chờ duyệt giao bù',
  DRAFT: 'Nháp',
  SUBMITTED: 'Đã gửi',
  CONFIRMED_QTY: 'Đã xác nhận số lượng',
  SHIPPED: 'Đã xuất kho',
  AUTO_RECEIVED: 'Tự động xác nhận',
  RECONCILED: 'Đã đối soát',
  CLOSED: 'Đã đóng',
  NPP_CONFIRMED: 'NPP đã xác nhận',
  PICKING: 'Đã xuất kho',
  DELIVERING: 'Đang giao',
  DELIVERED_SHORT: 'Giao thiếu',
  OPEN: 'Đang mở',
  PRE_CLOSE: 'Đang đối soát',
  LOCKED: 'Đã khóa',
  PUBLISHED: 'Đã công bố',
  PENDING_APPROVAL: 'Chờ duyệt',
  ON_HOLD_CREDIT: 'Giữ · Vượt hạn mức',
  APPROVED: 'Đã duyệt',
  NEED_INFO: 'Cần bổ sung',
  REJECTED: 'Từ chối',
  IN_DELIVERY: 'Đang giao',
  DELIVERED: 'Đã giao',
  RECEIVED: 'Đã nhận',
  RECEIVED_DISCREPANCY: 'Nhận có chênh lệch',
  CANCELLED: 'Đã hủy',
  PENDING_SYNC: 'Chờ gửi',
  PLANNED: 'Đã lên kế hoạch',
  COMPLETED: 'Hoàn tất',
  NEEDS_REVIEW: 'Chờ duyệt thủ công',
  AI_SCORED: 'Đã chấm AI',
  REVIEWED: 'Đã duyệt',
  PROCESSING: 'Đang xử lý',
  DONE: 'Hoàn tất',
  FAILED: 'Xử lý thất bại',
  PENDING: 'Đang chờ',
  DEAD: 'Cần xử lý lỗi',
  SETTLED: 'Đã quyết toán',
  PARTIALLY_APPROVED: 'Duyệt một phần',
  NEW: 'Mới',
  ACKNOWLEDGED: 'Đã tiếp nhận',
  IN_PROGRESS: 'Đang xử lý',
  WAITING_INFO: 'Chờ bổ sung',
  RESOLVED: 'Đã giải quyết',
  REOPENED: 'Mở lại',
  SENT: 'Đã gửi',
  ACCEPTED: 'Đã chấp nhận',
  RUNNING: 'Đang thực hiện',
  ADJUSTED: 'Đã điều chỉnh',
};
export const activeStatuses: Status[] = [
  'PENDING_APPROVAL',
  'ON_HOLD_CREDIT',
  'APPROVED',
  'NEED_INFO',
];
export function creditAvailable(db: DB, partnerId: string) {
  const p = db.partners.find((x) => x.id === partnerId);
  return p
    ? p.credit -
        p.debt -
        db.orders
          .filter(
            (o) =>
              o.partnerId === partnerId &&
              o.channel === 'sell-in' &&
              activeStatuses.includes(o.status),
          )
          .reduce((s, o) => s + total(o.lines), 0)
    : 0;
}
export function validateLines(
  lines: OrderLine[],
  stocks: Record<string, number>,
): string | undefined {
  if (!lines.length) return 'Vui lòng thêm ít nhất một sản phẩm.';
  for (const l of lines) {
    if (!Number.isInteger(l.qty) || l.qty <= 0) return 'Số lượng phải là số nguyên dương.';
    if (l.qty > (stocks[l.productId] ?? 0)) return 'Số lượng vượt quá tồn kho khả dụng.';
  }
}
export function canTransition(order: Order, next: Status) {
  const allowed: Partial<Record<Status, Status[]>> = {
    PENDING_APPROVAL: ['APPROVED', 'REJECTED', 'NEED_INFO', 'CANCELLED'],
    ON_HOLD_CREDIT: ['APPROVED', 'REJECTED', 'NEED_INFO', 'CANCELLED'],
    NEED_INFO: ['PENDING_APPROVAL', 'CANCELLED'],
    APPROVED: ['IN_DELIVERY'],
    IN_DELIVERY: ['DELIVERED'],
    DELIVERED: ['RECEIVED', 'RECEIVED_DISCREPANCY'],
    PENDING_SYNC: ['PENDING_APPROVAL'],
  };
  return allowed[order.status]?.includes(next) ?? false;
}
export function csvExport(name: string, rows: (string | number)[][]) {
  const data =
    '\uFEFF' +
    rows
      .map((row) => row.map((v) => '"' + String(v).replaceAll('"', '""') + '"').join(','))
      .join('\r\n');
  const url = URL.createObjectURL(new Blob([data], { type: 'text/csv;charset=utf-8;' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name + '.csv';
  a.click();
  URL.revokeObjectURL(url);
}
