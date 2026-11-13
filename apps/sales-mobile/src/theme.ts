// SYNERA brand tint with the user's explicitly requested light, neutral mobile surfaces.
const light = {
  canvas: '#f6f6f7',
  surface: '#ffffff',
  mutedSurface: '#f3f5f7',
  text: '#1f2937',
  muted: '#526071',
  border: '#e0e5eb',
  primary: '#1769c2',
  onPrimary: '#ffffff',
  accent: '#eaf3ff',
  heroBlue: '#1155aa',
  heroMid: '#69a5de',
  metricBlue: '#dfeeff',
  routeBlue: '#f0f7ff',
  danger: '#b42318',
};
const dark = {
  canvas: '#101c2b',
  surface: '#1c2939',
  mutedSurface: '#243448',
  text: '#e6edf6',
  muted: '#b8c7da',
  border: '#34485f',
  primary: '#73b9f4',
  onPrimary: '#101c2b',
  accent: '#193b60',
  danger: '#ffb4ab',
};
export type Colors = typeof light;
export function useColors() {
  return light;
}
export function statusLabel(status: string) {
  return (
    (
      {
        PLANNED: 'Chưa ghé',
        CHECKED_IN: 'Đang ghé',
        COMPLETED: 'Đã ghé',
        SUBMITTED: 'Đã gửi NPP',
        NPP_CONFIRMED: 'NPP đã xác nhận',
        PICKING: 'Đã xuất kho',
        DELIVERING: 'Đang giao',
        DELIVERED: 'Đã giao',
        DELIVERED_SHORT: 'Giao thiếu',
        PROPOSED: 'Chờ duyệt giao bù',
        APPROVED: 'Đã duyệt',
        SHIPPED: 'Đã xuất',
        RECEIVED: 'Đã ghi thực nhận',
        CANCELLED: 'Đã huỷ',
      } as Record<string, string>
    )[status] ||
    (
      {
        PENDING_APPROVAL: 'Chờ duyệt',
        CONFIRMED_QTY: 'Đã xác nhận',
        IN_DELIVERY: 'Đang giao',
        RECEIVED_DISCREPANCY: 'Nhận hàng có chênh lệch',
        DONE: 'Hoàn tất',
        OPEN: 'Đang mở',
      } as Record<string, string>
    )[status] ||
    'Đang cập nhật'
  );
}
export const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
