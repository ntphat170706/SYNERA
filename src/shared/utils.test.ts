import { describe, expect, it } from 'vitest';
import { canTransition, creditAvailable, total, validateLines } from './utils';
import { initialDB } from '../mocks/data';
describe('Credit and order rules', () => {
  it('includes debt and pending sell-in orders in the available credit', () => {
    const db = structuredClone(initialDB);
    db.orders = [
      {
        ...db.orders[0],
        partnerId: 'NPP-001',
        status: 'PENDING_APPROVAL',
        lines: [{ productId: 'SKU-001', qty: 100, price: 288000 }],
      },
    ];
    expect(creditAvailable(db, 'NPP-001')).toBe(500000000 - 285000000 - 28800000);
    db.orders[0].status = 'CANCELLED';
    expect(creditAvailable(db, 'NPP-001')).toBe(215000000);
  });
  it('does not count a sales order as manufacturer credit', () => {
    const db = structuredClone(initialDB);
    db.orders = [{ ...db.orders[0], channel: 'sell-out' }];
    expect(creditAvailable(db, 'NPP-001')).toBe(215000000);
  });
  it('rejects empty, fractional, negative and out-of-stock quantities', () => {
    expect(validateLines([], {})).toBeTruthy();
    for (const qty of [-1, 0, 1.5, 101])
      expect(validateLines([{ productId: 'A', qty, price: 100 }], { A: 100 })).toBeTruthy();
    expect(validateLines([{ productId: 'A', qty: 100, price: 100 }], { A: 100 })).toBeUndefined();
  });
  it('prevents closed or cancelled orders from being approved', () => {
    const order = initialDB.orders[0];
    expect(canTransition({ ...order, status: 'CANCELLED' }, 'APPROVED')).toBe(false);
    expect(canTransition({ ...order, status: 'RECEIVED' }, 'APPROVED')).toBe(false);
    expect(canTransition({ ...order, status: 'ON_HOLD_CREDIT' }, 'APPROVED')).toBe(true);
  });
  it('calculates line totals without unit ambiguity', () => {
    expect(
      total([
        { productId: 'A', qty: 10, price: 288000 },
        { productId: 'B', qty: 5, price: 96000 },
      ]),
    ).toBe(3360000);
  });
});
