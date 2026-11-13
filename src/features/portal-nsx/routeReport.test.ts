import { describe, it, expect } from 'vitest';
import { buildRouteReport } from './routeReport';
const data = {
  npps: [{ id: 2, name: 'NPP', region: 'SOUTH' }],
  stores: [{ id: 1, nppId: 2, repId: 20, name: 'Điểm đã bàn giao' }],
  staff: [{ id: 20, name: 'Người nhận', nppId: 2, role: 'SALES' }],
  people: [{ id: 10, name: 'Người bàn giao' }],
  sellOut: [
    { id: 1, nppId: 2, storeId: 1, repId: 10, status: 'DELIVERED', total: 100 },
    { id: 2, nppId: 2, storeId: 1, repId: 20, status: 'SUBMITTED', total: 50 },
  ],
  visits: [
    {
      id: 1,
      nppId: 2,
      storeId: 1,
      repId: 10,
      status: 'COMPLETED',
      checkedInAt: '2026-11-01T01:00:00Z',
      checkedOutAt: '2026-11-01T01:10:00Z',
    },
  ],
};
const filter = { region: '', npp: '', rep: '', route: '', group: 'sales' };
describe('manufacturer report preserves responsibility history', () => {
  it('retains former Sales results after a store changes current owner', () => {
    const report = buildRouteReport(data, filter),
      old = report.find((r) => r.id === '10')!;
    expect(old.own).toHaveLength(0);
    expect(old.name).toBe('Người bàn giao');
    expect(old.value).toBe(100);
    expect(old.minutes).toBe(10);
    expect(report.reduce((s, r) => s + r.value, 0)).toBe(150);
  });
  it('filtering former Sales does not lose the historic order or visit', () => {
    const report = buildRouteReport(data, { ...filter, rep: '10', group: 'npp' });
    expect(report[0].value).toBe(100);
    expect(report[0].planned).toBe(1);
  });
});
