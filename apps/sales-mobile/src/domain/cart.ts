import type { CartLine, Offering } from './contracts.ts';
export const lineKey = (line: Pick<CartLine, 'skuId' | 'uom'>) => `${line.skuId}:${line.uom}`;
export const unitLabel = (value: string) =>
  ({ CHAI: 'Chai', LOC: 'Lốc', THUNG: 'Thùng', HOP: 'Hộp', GOI: 'Gói' })[value] || value;
export function cartLines(cart: Record<string, number>, offerings: Offering[]): CartLine[] {
  if (
    Object.entries(cart).some(([key, qty]) => qty > 0 && !offerings.some((o) => lineKey(o) === key))
  )
    throw Error('Có mặt hàng hoặc đơn vị không còn được cung cấp. Kiểm tra lại giỏ hàng.');
  return offerings
    .filter((o) => (cart[lineKey(o)] || 0) > 0)
    .map((o) => {
      const qty = cart[lineKey(o)];
      if (
        !Number.isFinite(qty) ||
        qty <= 0 ||
        Math.abs(qty / o.multiple - Math.round(qty / o.multiple)) > 1e-8 ||
        qty * o.factor < o.minOrder
      )
        throw Error(`Số lượng ${o.name} chưa đúng bội số hoặc tối thiểu.`);
      return { skuId: o.skuId, uom: o.uom, qty };
    });
}
export function stepQuantity(value: number, step: number, direction: number) {
  return Math.max(0, Math.round((value + step * direction) * 1000) / 1000);
}
