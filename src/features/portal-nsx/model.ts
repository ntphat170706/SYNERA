import { numberValue, records, textValue, type RecordData } from '../../shared/api';
export const rows = (data: RecordData, key: string) => records(data[key]);
export const regionName = (value: unknown) =>
  ({ SOUTH: 'Miền Nam', NORTH: 'Miền Bắc', CENTRAL: 'Miền Trung', ALL: 'Toàn quốc' })[
    textValue(value)
  ] ||
  textValue(value) ||
  'Chưa phân vùng';
export const roleName = (value: unknown) =>
  ({
    SALES: 'Nhân viên Sales',
    SUPERVISOR: 'Giám sát bán hàng',
    NPP_OWNER: 'Đầu mối NPP',
    WAREHOUSE_NPP: 'Kho NPP',
  })[textValue(value)] || 'Nhân sự phối hợp';
export const dateText = (value: unknown) =>
  value ? new Date(textValue(value)).toLocaleDateString('vi-VN') : '—';
export const personName = (data: RecordData, id: unknown) =>
  textValue(
    [...rows(data, 'staff'), ...rows(data, 'people')].find((p) => String(p.id) === String(id))
      ?.name,
  ) || (id ? 'Đang cập nhật người phụ trách' : 'Chưa phân công');
export const organizationKinds: Record<string, string> = {
  NSX: 'Nhà sản xuất',
  REGION: 'Vùng bán hàng',
  BRANCH: 'Chi nhánh',
  NPP: 'Nhà phân phối',
};
export const nppName = (data: RecordData, id: unknown) =>
  textValue(rows(data, 'npps').find((p) => String(p.id) === String(id))?.name) || 'Nhà phân phối';
export const storeName = (data: RecordData, id: unknown) =>
  textValue(rows(data, 'stores').find((p) => String(p.id) === String(id))?.name) || 'Điểm bán';
export const orderAmount = (list: RecordData[]) =>
  list
    .filter((o) => !['CANCELLED', 'REJECTED', 'DRAFT'].includes(textValue(o.status)))
    .reduce((a, o) => a + numberValue(o.total), 0);
export const isOpenCase = (r: RecordData) =>
  r.status !== 'CANCELLED' && (r.status !== 'RECEIVED' || !r.notifiedAt);
export function statsFor(data: RecordData, id: unknown) {
  const own = (key: string) => rows(data, key).filter((r) => String(r.nppId) === String(id));
  return {
    inbound: orderAmount(own('sellIn')),
    outbound: orderAmount(own('sellOut')),
    stores: own('stores').length,
    pending: own('sellIn').filter((o) =>
      ['PENDING_APPROVAL', 'ON_HOLD_CREDIT'].includes(textValue(o.status)),
    ).length,
    activeSku: new Set(
      own('stock')
        .filter((s) => numberValue(s.available) > 0)
        .map((s) => s.skuId),
    ).size,
  };
}
