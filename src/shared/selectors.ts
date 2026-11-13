import type { DB, Order, Role } from './types';
import { today, total } from './utils';

export type DashboardFilter = { period: string; region: string; referenceDate?: string };
const excluded = new Set(['CANCELLED', 'REJECTED', 'PENDING_SYNC']);

/** Orders are operational values, not recognized revenue. Never infer revenue from shipping status. */
export function selectDashboard(db: DB, role: Role, filter: DashboardFilter) {
  const end = filter.referenceDate || today();
  const date = new Date(end + 'T12:00:00');
  const start = new Date(date);
  if (filter.period === 'week') start.setDate(date.getDate() - 6);
  else if (filter.period === 'quarter') start.setMonth(Math.floor(date.getMonth() / 3) * 3, 1);
  else start.setDate(1);
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const from = iso(start);
  const partners = db.partners.filter(
    (p) =>
      (filter.region === 'all' || p.region === filter.region) &&
      (role === 'nsx' || p.id === 'NPP-001'),
  );
  const ids = new Set(partners.map((p) => p.id));
  const orders = db.orders.filter(
    (o) => o.channel === 'sell-in' && ids.has(o.partnerId) && o.date >= from && o.date <= end,
  );
  const value = orders
    .filter((o) => !excluded.has(o.status))
    .reduce((sum, o) => sum + total(o.lines), 0);
  const sellOut = ids.has('NPP-001')
    ? db.orders.filter(
        (o) =>
          o.channel === 'sell-out' && o.date >= from && o.date <= end && !excluded.has(o.status),
      )
    : [];
  const dates: string[] = [];
  for (const cursor = new Date(start); iso(cursor) <= end; cursor.setDate(cursor.getDate() + 1))
    dates.push(iso(cursor));
  const perDay = (rows: Order[], amount: boolean) =>
    dates.map((day) =>
      rows
        .filter((o) => o.date === day && !excluded.has(o.status))
        .reduce((sum, o) => sum + (amount ? total(o.lines) / 1e6 : 1), 0),
    );
  return {
    partners,
    orders,
    value,
    dates,
    labels: dates.map((day) => day.slice(8) + '/' + day.slice(5, 7)),
    sellIn: perDay(orders, true),
    sellOut: perDay(sellOut, true),
    created: perDay(orders, false),
    received: perDay(
      orders.filter((o) => o.status === 'RECEIVED'),
      false,
    ),
  };
}
