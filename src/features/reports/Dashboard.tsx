import { useState } from 'react';
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js';
import { Doughnut } from 'react-chartjs-2';
import { useStore } from '../../shared/store';
import { useApi } from '../../shared/hooks/useApi';
import { command, download, textValue, numberValue, type RecordData } from '../../shared/api';
import { ActionForm, LoadState } from '../../shared/ApiUI';
import { PageHead, Panel, Table, Badge } from '../../shared/ui';
import { chartTheme } from '../../shared/chartTheme';
import { compact, money, statusLabels } from '../../shared/utils';
import Exports from './Exports';
ChartJS.register(ArcElement, Tooltip, Legend);
export default function Dashboard({ reports = false }: { reports?: boolean }) {
  const { db, role, has, notify } = useStore();
  const summary = useApi<RecordData>(has('REPORT_READ') ? '/reports/dashboard' : null, {}),
    tasks = useApi<RecordData[]>('/tasks', []),
    approvals = useApi<RecordData[]>('/approvals', []),
    periods = useApi<RecordData[]>(role !== 'sales' ? '/closing/periods' : null, []);
  const alerts = useApi<RecordData[]>(has('REPORT_READ') ? '/operations/alerts' : null, []);
  const [ack, setAck] = useState<RecordData | null>(null);
  const [period, setPeriod] = useState(''),
    [form, setForm] = useState(false);
  const periodId =
    period ||
    textValue(
      periods.data.find((p) => ['CLOSED', 'LOCKED', 'PUBLISHED'].includes(textValue(p.status)))?.id,
    );
  const snapshots = useApi<RecordData[]>(
      periodId ? `/closing/periods/${periodId}/snapshots` : null,
      [],
    ),
    targets = useApi<RecordData[]>(
      periodId ? `/performance/targets?periodId=${periodId}` : null,
      [],
    );
  const amounts = Object.entries(
    db.orders.reduce<Record<string, number>>(
      (counts, o) => ({ ...counts, [o.status]: (counts[o.status] || 0) + 1 }),
      {},
    ),
  );
  const theme = chartTheme();
  return (
    <>
      <PageHead
        title={reports ? 'Báo cáo & chỉ tiêu' : 'Tổng quan hoạt động'}
        description={
          has('REPORT_READ')
            ? `Tạm tính · cập nhật ${summary.data.generatedAt ? new Date(textValue(summary.data.generatedAt)).toLocaleString('vi-VN') : 'từ máy chủ'}`
            : undefined
        }
      >
        {role !== 'npp' && has('REPORT_READ') && (
          <button className="btn" onClick={summary.reload}>
            Làm mới
          </button>
        )}
        {role !== 'sales' && has('REPORT_READ') && (
          <button
            className="btn"
            onClick={() =>
              void download('/reports/orders.xlsx', 'synera-don-hang.xlsx').catch((e) =>
                notify(e.message),
              )
            }
          >
            Xuất Excel
          </button>
        )}
        {reports && (has('ORDER_APPROVE') || has('TRADE_APPROVE')) && (
          <button className="btn primary" onClick={() => setForm(true)}>
            Giao chỉ tiêu
          </button>
        )}
      </PageHead>
      <LoadState {...summary} retry={summary.reload} />
      <div className="stats-grid">
        {[
          ['Đơn hàng', numberValue(summary.data.orders), false],
          [
            'Giá trị đơn tạm tính',
            numberValue(summary.data.sellInOrderValue || summary.data.orderValue),
            true,
          ],
          ['Đang chờ xử lý', numberValue(summary.data.pending), false],
          [
            role === 'sales' ? 'Lượt ghé hôm nay' : 'Dư nợ',
            numberValue(role === 'sales' ? summary.data.visits : summary.data.debt),
            role !== 'sales',
          ],
        ].map(([label, value, currency]) => (
          <article className="stat-card" key={String(label)}>
            <span className="stat-top">{label}</span>
            <strong className="big-number">
              {currency ? compact(Number(value)) : Number(value).toLocaleString('vi-VN')}
            </strong>
            {currency && <small className="stat-bottom">VND</small>}
          </article>
        ))}
      </div>
      <div className="chart-grid">
        <Panel title="Phân bổ trạng thái đơn">
          {amounts.length ? (
            <div className="chart-container">
              <Doughnut
                data={{
                  labels: amounts.map(([status]) => statusLabels[status] || status),
                  datasets: [
                    {
                      data: amounts.map(([, count]) => count),
                      backgroundColor: theme.status,
                      borderColor: theme.surface,
                      borderWidth: 3,
                    },
                  ],
                }}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: { legend: { position: 'bottom', labels: { color: theme.metadata } } },
                }}
              />
            </div>
          ) : (
            <p className="empty">Chưa có đơn hàng.</p>
          )}
        </Panel>
        <Panel title="Công việc cần làm" className="report-task-panel">
          <LoadState {...tasks} retry={tasks.reload} />
          <Table
            rows={tasks.data}
            columns={[
              { key: 'title', label: 'Công việc', render: (t) => textValue(t.title) },
              {
                key: 'dueAt',
                label: 'Hạn',
                render: (t) => new Date(textValue(t.dueAt)).toLocaleString('vi-VN'),
              },
              {
                key: 'status',
                label: 'Trạng thái',
                render: (t) => <Badge status={textValue(t.status)} />,
              },
              {
                key: 'action',
                label: 'Xử lý',
                render: (t) =>
                  t.status === 'OPEN' ? (
                    <button
                      className="btn"
                      onClick={() =>
                        void command(`/tasks/${t.id}/complete`)
                          .then(tasks.reload)
                          .catch((e) => notify(e.message))
                      }
                    >
                      Hoàn thành
                    </button>
                  ) : null,
              },
            ]}
          />
        </Panel>
      </div>
      {role !== 'sales' && (
        <Panel
          title="Số liệu đã chốt"
          action={
            <select
              aria-label="Kỳ báo cáo"
              value={periodId}
              onChange={(e) => setPeriod(e.target.value)}
            >
              <option value="">Chọn kỳ</option>
              {periods.data.map((p) => (
                <option key={textValue(p.id)} value={textValue(p.id)}>
                  {textValue(p.type)} · {textValue(p.startDate)} · {textValue(p.status)}
                </option>
              ))}
            </select>
          }
        >
          <LoadState {...snapshots} retry={snapshots.reload} />
          <Table
            rows={snapshots.data}
            columns={[
              {
                key: 'npp',
                label: 'Nhà phân phối',
                render: (r) =>
                  db.partners.find((p) => p.id === textValue(r.nppId))?.name || textValue(r.nppId),
              },
              {
                key: 'amount',
                label: 'Doanh số đã chốt',
                render: (r) => money(numberValue(r.amount)),
              },
              { key: 'debt', label: 'Dư nợ tại chốt', render: (r) => money(numberValue(r.debt)) },
              { key: 'at', label: 'Chốt lúc', render: (r) => textValue(r.capturedAt) },
            ]}
          />
          {reports && (
            <>
              <h3>Chỉ tiêu & thưởng</h3>
              <Table
                rows={targets.data}
                columns={[
                  { key: 'nppId', label: 'NPP', render: (r) => textValue(r.nppId) },
                  { key: 'target', label: 'Chỉ tiêu', render: (r) => money(numberValue(r.target)) },
                  {
                    key: 'closedAmount',
                    label: 'Đã chốt',
                    render: (r) => money(numberValue(r.closedAmount)),
                  },
                  {
                    key: 'reward',
                    label: 'Thưởng đủ điều kiện',
                    render: (r) => money(numberValue(r.reward)),
                  },
                ]}
              />
            </>
          )}
        </Panel>
      )}
      {has('REPORT_READ') && (
        <Panel title="Cảnh báo vận hành">
          <LoadState {...alerts} retry={alerts.reload} />
          <Table
            rows={alerts.data}
            columns={[
              { key: 'category', label: 'Loại', render: (r) => textValue(r.category) },
              { key: 'detail', label: 'Nội dung', render: (r) => textValue(r.detail) },
              {
                key: 'status',
                label: 'Trạng thái',
                render: (r) => <Badge status={textValue(r.status)} />,
              },
              {
                key: 'action',
                label: 'Xử lý',
                render: (r) =>
                  r.status === 'OPEN' ? (
                    <button className="btn" onClick={() => setAck(r)}>
                      Đã tiếp nhận
                    </button>
                  ) : null,
              },
            ]}
          />
        </Panel>
      )}
      {ack && (
        <ActionForm
          title="Tiếp nhận cảnh báo"
          fields={[{ key: 'reason', label: 'Ghi chú xử lý', type: 'textarea' }]}
          onClose={() => setAck(null)}
          onSubmit={async (values) => {
            await command(`/operations/alerts/${ack.id}/acknowledge`, values);
            alerts.reload();
            notify('Đã tiếp nhận cảnh báo.');
          }}
        />
      )}
      {approvals.data.length > 0 && (
        <Panel title="Phê duyệt đang chờ">
          <Table
            rows={approvals.data}
            columns={[
              { key: 'entityType', label: 'Loại tài liệu', render: (r) => textValue(r.entityType) },
              { key: 'entityId', label: 'Số', render: (r) => textValue(r.entityId) },
              { key: 'step', label: 'Bước', render: (r) => textValue(r.step) },
              {
                key: 'requiredRole',
                label: 'Vai trò cần duyệt',
                render: (r) => textValue(r.requiredRole),
              },
            ]}
          />
        </Panel>
      )}
      {form && (
        <ActionForm
          title="Giao chỉ tiêu"
          fields={[
            {
              key: 'nppId',
              label: 'Nhà phân phối',
              options: db.partners.map((p) => ({ value: p.id, label: p.name })),
            },
            {
              key: 'periodId',
              label: 'Kỳ',
              options: periods.data.map((p) => ({
                value: textValue(p.id),
                label: `${p.type} ${p.startDate}`,
              })),
            },
            { key: 'amount', label: 'Chỉ tiêu doanh số', type: 'number', min: 0.01, step: '0.01' },
            { key: 'rewardPct', label: 'Tỷ lệ thưởng (%)', type: 'number', min: 0, step: '0.01' },
          ]}
          onClose={() => setForm(false)}
          onSubmit={async (values) => {
            await command(
              '/performance/targets',
              Object.fromEntries(Object.entries(values).map(([k, v]) => [k, Number(v)])),
            );
            targets.reload();
            notify('Đã giao chỉ tiêu.');
          }}
        />
      )}
      {reports && <Exports />}
    </>
  );
}
