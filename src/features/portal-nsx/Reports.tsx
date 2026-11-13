import { buildRouteReport } from './routeReport';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Download } from 'lucide-react';
import { useApi } from '../../shared/hooks/useApi';
import { textValue, type RecordData } from '../../shared/api';
import { LoadState } from '../../shared/ApiUI';
import { PageHead } from '../../shared/ui';
import { money, today } from '../../shared/utils';
import { rows, regionName, dateText } from './model';
import { DataStamp } from './RecordParts';
export default function Reports() {
  const [from, setFrom] = useState(today().slice(0, 7) + '-01'),
    [to, setTo] = useState(today());
  const [region, setRegion] = useState(''),
    [npp, setNpp] = useState(''),
    [rep, setRep] = useState(''),
    [route, setRoute] = useState(''),
    [group, setGroup] = useState('sales');
  const [applied, setApplied] = useState({ from, to });
  const result = useApi<RecordData>(
      `/manufacturer/network?from=${applied.from}&to=${applied.to}`,
      {},
    ),
    data = result.data;
  const npps = rows(data, 'npps'),
    staff = rows(data, 'staff'),
    routes = rows(data, 'routes');
  const allowedNpps = npps.filter(
    (n) => (!region || n.region === region) && (!npp || String(n.id) === npp),
  );
  const ids = new Set(allowedNpps.map((n) => String(n.id)));
  const report = buildRouteReport(data, { region, npp, rep, route, group });
  function exportFile() {
    const lines = [
      [
        'Đơn vị',
        'Điểm bán',
        'Lượt kế hoạch',
        'Đã ghé',
        'Hoàn thành',
        'Tỷ lệ hoàn thành',
        'Phút ghé TB',
        'Đơn bán',
        'Đã giao đủ',
        'Giao thiếu',
        'Giá trị đơn',
      ],
      ...report.map((r) => [
        r.name,
        r.own.length,
        r.planned,
        r.visited,
        r.complete,
        r.ratio ?? '',
        r.minutes ?? '',
        r.orders,
        r.delivered,
        r.short,
        r.value,
      ]),
    ];
    const csv =
      '\uFEFF' +
      lines
        .map((line) =>
          line
            .map(
              (v) =>
                '"' +
                (typeof v === 'string' && /^[=+\-@\t\r]/.test(v) ? "'" + v : String(v)).replaceAll(
                  '"',
                  '""',
                ) +
                '"',
            )
            .join(','),
        )
        .join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })),
      a = document.createElement('a');
    a.href = url;
    a.download = `synera-di-tuyen-${applied.from}-${applied.to}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return (
    <div className="nsx-report-page">
      <div className="nsx-breadcrumb">
        <span>Báo cáo</span>
        <span>/</span>
        <strong>Đi tuyến & kết quả bán hàng</strong>
      </div>
      <PageHead title="Báo cáo đi tuyến & bán hàng">
        <button className="btn" disabled={!report.length} onClick={exportFile}>
          <Download size={16} />
          Xuất CSV
        </button>
      </PageHead>
      <form
        className="nsx-report-filters"
        onSubmit={(e) => {
          e.preventDefault();
          setApplied({ from, to });
        }}
      >
        <div className="nsx-filter-grid">
          <label>
            Từ ngày
            <input
              type="date"
              required
              value={from}
              max={to}
              onChange={(e) => setFrom(e.target.value)}
            />
          </label>
          <label>
            Đến ngày
            <input
              type="date"
              required
              min={from}
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </label>
          <label>
            Vùng bán hàng
            <select
              value={region}
              onChange={(e) => {
                setRegion(e.target.value);
                setNpp('');
                setRep('');
                setRoute('');
              }}
            >
              <option value="">Tất cả vùng</option>
              {[...new Set(npps.map((n) => textValue(n.region)))].map((r) => (
                <option key={r} value={r}>
                  {regionName(r)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Nhà phân phối
            <select
              value={npp}
              onChange={(e) => {
                setNpp(e.target.value);
                setRep('');
                setRoute('');
              }}
            >
              <option value="">Tất cả NPP</option>
              {npps
                .filter((n) => !region || n.region === region)
                .map((n) => (
                  <option key={textValue(n.id)} value={textValue(n.id)}>
                    {textValue(n.name)}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Nhân viên bán hàng
            <select value={rep} onChange={(e) => setRep(e.target.value)}>
              <option value="">Tất cả Sales</option>
              {staff
                .filter((p) => p.role === 'SALES' && ids.has(String(p.nppId)))
                .filter((p, i, a) => a.findIndex((x) => x.id === p.id) === i)
                .map((p) => (
                  <option key={textValue(p.id)} value={textValue(p.id)}>
                    {textValue(p.name)}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Tuyến bán hàng
            <select value={route} onChange={(e) => setRoute(e.target.value)}>
              <option value="">Tất cả tuyến</option>
              {routes
                .filter((r) => ids.has(String(r.nppId)))
                .map((r) => (
                  <option key={textValue(r.id)} value={textValue(r.id)}>
                    {textValue(r.name)}
                  </option>
                ))}
            </select>
          </label>
        </div>
        <button className="btn primary" type="submit">
          Lọc dữ liệu
        </button>
      </form>
      <LoadState {...result} retry={result.reload} />
      <section className="nsx-data-surface">
        <nav className="nsx-report-group" aria-label="Nhóm báo cáo">
          <span>Nhóm theo</span>
          {[
            ['sales', 'Nhân viên'],
            ['npp', 'Nhà phân phối'],
            ['store', 'Điểm bán'],
          ].map(([v, label]) => (
            <button key={v} className={group === v ? 'active' : ''} onClick={() => setGroup(v)}>
              {label}
            </button>
          ))}
        </nav>
        <h2 className="nsx-section-heading">Bảng thống kê</h2>
        <div className="nsx-report-scroll">
          <table className="nsx-grouped-report">
            <thead>
              <tr>
                <th rowSpan={2}>
                  {group === 'sales'
                    ? 'Nhân viên bán hàng'
                    : group === 'npp'
                      ? 'Nhà phân phối'
                      : 'Điểm bán'}
                </th>
                <th colSpan={6}>ĐI TUYẾN</th>
                <th colSpan={4} className="nsx-sales-head">
                  KẾT QUẢ BÁN HÀNG
                </th>
              </tr>
              <tr>
                {[
                  'Điểm phụ trách',
                  'Lượt kế hoạch',
                  'Đã ghé',
                  'Hoàn thành',
                  'Tỷ lệ hoàn thành',
                  'Thời gian TB',
                  'Đơn bán',
                  'Đã giao đủ',
                  'Giao thiếu',
                  'Giá trị đơn',
                ].map((label) => (
                  <th key={label}>{label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {report.map((r) => (
                <tr key={r.id}>
                  <th>
                    <Link to={r.path}>{r.name}</Link>
                  </th>
                  <td>{r.own.length}</td>
                  <td>{r.planned}</td>
                  <td>{r.visited}</td>
                  <td>{r.complete}</td>
                  <td className={r.ratio !== null && r.ratio < 80 ? 'nsx-text-attention' : ''}>
                    {r.ratio === null ? '—' : `${r.ratio}%`}
                  </td>
                  <td>{r.minutes === null ? '—' : `${r.minutes} phút`}</td>
                  <td>{r.orders}</td>
                  <td>{r.delivered}</td>
                  <td className={r.short ? 'nsx-text-danger' : ''}>{r.short}</td>
                  <td>{money(r.value)}</td>
                </tr>
              ))}
            </tbody>
            {report.length > 0 && (
              <tfoot>
                <tr>
                  <th>Tổng cộng</th>
                  {['own', 'planned', 'visited', 'complete'].map((k) => (
                    <td key={k}>
                      {report.reduce(
                        (s, r) => s + (k === 'own' ? r.own.length : Number(r[k as keyof typeof r])),
                        0,
                      )}
                    </td>
                  ))}
                  <td>—</td>
                  <td>—</td>
                  {['orders', 'delivered', 'short'].map((k) => (
                    <td key={k}>
                      {report.reduce((s, r) => s + Number(r[k as keyof typeof r]), 0)}
                    </td>
                  ))}
                  <td>{money(report.reduce((s, r) => s + r.value, 0))}</td>
                </tr>
              </tfoot>
            )}
          </table>
          {!result.loading && !result.error && !report.length && (
            <div className="empty">Không có dữ liệu trong bộ lọc đã chọn.</div>
          )}
        </div>
        <div className="nsx-report-source">
          <span>
            {dateText(applied.from)} – {dateText(applied.to)}
          </span>
          <span>Đơn tạo trong kỳ · Trạng thái hiện tại</span>
        </div>
      </section>
      <DataStamp data={data} />
    </div>
  );
}
