import type { RecordData } from '../../shared/api';
import { rows, personName, orderAmount } from './model';
export function buildRouteReport(
  data: RecordData,
  filter: { region: string; npp: string; rep: string; route: string; group: string },
) {
  const { region, npp, rep, route, group } = filter;
  const npps = rows(data, 'npps').filter(
    (n) => (!region || n.region === region) && (!npp || String(n.id) === npp),
  );
  const nppIds = new Set(npps.map((n) => String(n.id))),
    chosen = rows(data, 'routes').find((r) => String(r.id) === route);
  const stops = Array.isArray(chosen?.stops) ? (chosen.stops as RecordData[]) : [],
    routeIds = new Set(stops.map((s) => String(s.storeId)));
  const outlets = rows(data, 'stores').filter(
    (s) => nppIds.has(String(s.nppId)) && (!route || routeIds.has(String(s.id))),
  );
  const ids = new Set(outlets.map((s) => String(s.id)));
  const orders = rows(data, 'sellOut').filter(
    (o) => ids.has(String(o.storeId)) && (!rep || String(o.repId) === rep),
  );
  const visits = rows(data, 'visits').filter(
    (v) => ids.has(String(v.storeId)) && (!rep || String(v.repId) === rep),
  );
  const repIds = new Set([
    ...rows(data, 'staff')
      .filter((p) => p.role === 'SALES' && nppIds.has(String(p.nppId)))
      .map((p) => String(p.id)),
    ...orders.map((o) => String(o.repId)),
    ...visits.map((v) => String(v.repId)),
  ]);
  const groups =
    group === 'npp'
      ? npps.map((n) => ({
          id: String(n.id),
          name: String(n.name),
          path: `/network?view=npps&id=${n.id}`,
          own: outlets.filter((s) => s.nppId === n.id && (!rep || String(s.repId) === rep)),
        }))
      : group === 'store'
        ? outlets
            .filter(
              (s) =>
                !rep ||
                String(s.repId) === rep ||
                orders.some((o) => o.storeId === s.id) ||
                visits.some((v) => v.storeId === s.id),
            )
            .map((s) => ({
              id: String(s.id),
              name: String(s.name),
              path: `/network?view=stores&id=${s.id}`,
              own: [s],
            }))
        : [...repIds]
            .filter((id) => !rep || id === rep)
            .map((id) => ({
              id,
              name: personName(data, id),
              path: '/network?view=routes',
              own: outlets.filter((s) => String(s.repId) === id),
            }));
  return groups.map((g) => {
    const match = (r: RecordData) =>
      String(r[group === 'npp' ? 'nppId' : group === 'store' ? 'storeId' : 'repId']) === g.id;
    const v = visits.filter(match),
      o = orders.filter(match),
      complete = v.filter((v) => v.status === 'COMPLETED');
    const durations = complete
      .map(
        (v) =>
          (new Date(String(v.checkedOutAt)).getTime() - new Date(String(v.checkedInAt)).getTime()) /
          60000,
      )
      .filter((n) => Number.isFinite(n) && n >= 0);
    return {
      ...g,
      planned: v.length,
      visited: v.filter((v) => v.status !== 'PLANNED').length,
      complete: complete.length,
      ratio: v.length ? Math.round((complete.length / v.length) * 100) : null,
      minutes: durations.length
        ? Math.round(durations.reduce((s, n) => s + n, 0) / durations.length)
        : null,
      orders: o.filter((o) => o.status !== 'CANCELLED').length,
      delivered: o.filter((o) => o.status === 'DELIVERED').length,
      short: o.filter((o) => o.status === 'DELIVERED_SHORT').length,
      value: orderAmount(o),
    };
  });
}
