import { describe, it, expect } from 'vitest';
import { selectDashboard } from './selectors';
import { initialDB } from '../mocks/data';
describe('Dashboard uses source orders', () => {
  it('does not invent future values, growth or period multipliers', () => {
    const db = structuredClone(initialDB);
    db.orders = [
      {
        ...db.orders[0],
        lines: [{ productId: 'SKU-001', price: 100000, qty: 2 }],
        date: '2026-11-10',
      },
    ];
    const result = selectDashboard(db, 'nsx', {
      period: 'month',
      region: 'all',
      referenceDate: '2026-11-10',
    });
    expect(result.value).toBe(200000);
    expect(result.labels.at(-1)).toBe('10/11');
    expect(result.sellIn.reduce((a, b) => a + b, 0)).toBe(0.2);
    expect(result.sellOut.every((x) => x === 0)).toBe(true);
  });
  it('excludes cancelled values and scopes NPP results', () => {
    const db = structuredClone(initialDB);
    db.orders = [
      { ...db.orders[0], partnerId: 'NPP-002' },
      { ...db.orders[0], status: 'CANCELLED' },
    ];
    const result = selectDashboard(db, 'npp', {
      period: 'month',
      region: 'all',
      referenceDate: '2026-11-10',
    });
    expect(result.orders).toHaveLength(1);
    expect(result.value).toBe(0);
  });
});
