import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ShoppingBag,
  PackageCheck,
  Truck,
  AlertTriangle,
  Plus,
  FileSpreadsheet,
  WandSparkles,
  Download,
  CheckCheck,
  Clock3,
} from 'lucide-react';
import { useApi } from '../../shared/hooks/useApi';
import { useStore } from '../../shared/store';
import { type RecordData, textValue } from '../../shared/api';
import { orderInQueue } from '../../shared/nppOperations';
import { PageHead, Panel, Table, Badge, SearchBox } from '../../shared/ui';
import { LoadState } from '../../shared/ApiUI';
import { money } from '../../shared/utils';

type WorkOrder = RecordData & { channel: string };
const nextAction: Record<string, string> = {
  SUBMITTED: 'Xác nhận đơn',
  NPP_CONFIRMED: 'Xuất kho',
  PICKING: 'Bắt đầu giao',
  DELIVERING: 'Ghi thực nhận',
  DELIVERED: 'Nhận hàng',
  DELIVERED_SHORT: 'Xử lý giao thiếu',
  RECEIVED_DISCREPANCY: 'Xem chênh lệch',
};
export default function Overview() {
  const { db, has } = useStore();
  const inbound = useApi<RecordData[]>('/sell-in-orders', []);
  const outbound = useApi<RecordData[]>('/sales-orders', []);
  const replacements = useApi<RecordData[]>('/sales-replacements', []);
  const [queue, setQueue] = useState('confirm');
  const [query, setQuery] = useState('');
  const storeName = (id: unknown) =>
    db.stores.find((s) => s.id === String(id))?.name || `Điểm bán #${id}`;
  const orderLink = (o: WorkOrder) => `/orders?channel=${o.channel}&order=${o.id}`;
  const sales = outbound.data.map<WorkOrder>((o) => ({ ...o, channel: 'sales' }));
  const receipts = inbound.data
    .filter((o) => orderInQueue(o, 'receiving', 'sellin', 'npp'))
    .map<WorkOrder>((o) => ({ ...o, channel: 'sellin' }));
  const exceptions: WorkOrder[] = [
    ...inbound.data
      .filter((o) => orderInQueue(o, 'short', 'sellin', 'npp'))
      .map<WorkOrder>((o) => ({ ...o, channel: 'sellin' })),
    ...sales.filter((o) => orderInQueue(o, 'short', 'sales', 'npp')),
  ];
  const queues = [
    {
      id: 'confirm',
      title: 'Đơn chờ xác nhận',
      tone: 'sales',
      icon: ShoppingBag,
      rows: sales.filter((o) => o.status === 'SUBMITTED'),
      loading: outbound.loading,
      error: outbound.error,
    },
    {
      id: 'fulfillment',
      title: 'Xuất & giao hàng',
      tone: 'stock',
      icon: Truck,
      rows: sales.filter((o) =>
        ['NPP_CONFIRMED', 'PICKING', 'DELIVERING'].includes(textValue(o.status)),
      ),
      loading: outbound.loading,
      error: outbound.error,
    },
    {
      id: 'receiving',
      title: 'Nhận hàng từ NSX',
      tone: 'inbound',
      icon: PackageCheck,
      rows: receipts,
      loading: inbound.loading,
      error: inbound.error,
    },
    {
      id: 'short',
      title: 'Giao nhận chênh lệch',
      tone: 'attention',
      icon: AlertTriangle,
      rows: exceptions,
      loading: inbound.loading || outbound.loading,
      error: inbound.error || outbound.error,
    },
  ];
  const activeQueue = queues.find((q) => q.id === queue)!;
  const visibleOrders = activeQueue.rows.filter((o) =>
    `${o.code} ${storeName(o.storeId)}`
      .toLocaleLowerCase('vi')
      .includes(query.toLocaleLowerCase('vi')),
  );
  const followups = replacements.data
    .filter((r) => !r.notifiedAt)
    .sort(
      (a, b) => new Date(textValue(a.dueAt)).getTime() - new Date(textValue(b.dueAt)).getTime(),
    );
  const date = new Date().toLocaleDateString('vi-VN', {
    weekday: 'long',
    day: 'numeric',
    month: 'numeric',
    year: 'numeric',
  });
  return (
    <div className="npp-workspace">
      <PageHead title="Điều hành hôm nay" description={date}>
        {has('REPORT_READ') && (
          <Link className="btn" to="/reports?export=SELL_OUT">
            <Download size={16} />
            Xuất Excel
          </Link>
        )}
        {has('SELLIN_CREATE') && (
          <Link className="btn primary" to="/catalog">
            <Plus size={17} />
            Đặt nhập từ NSX
          </Link>
        )}
      </PageHead>

      <LoadState {...inbound} retry={inbound.reload} />
      <LoadState {...outbound} retry={outbound.reload} />
      <LoadState {...replacements} retry={replacements.reload} />
      <div className="npp-work-lanes" aria-label="Hàng đợi vận hành">
        {queues.map(({ id, title, tone, icon: Icon, rows, loading, error }) => (
          <button
            key={id}
            className={`npp-work-lane tone-${tone} ${queue === id ? 'selected' : ''}`}
            aria-pressed={queue === id}
            onClick={() => {
              setQueue(id);
              setQuery('');
            }}
          >
            <Icon size={21} />
            <span>
              {title}
              <strong>{loading || error ? '—' : rows.length.toLocaleString('vi-VN')}</strong>
            </span>
            <ArrowRight size={17} />
          </button>
        ))}
      </div>
      <Panel
        title={activeQueue.title}
        className={`npp-operations-table tone-${activeQueue.tone}`}
        action={
          <Link
            className="text-button"
            to={
              queue === 'short'
                ? '/tickets'
                : `/orders?channel=${queue === 'receiving' ? 'sellin' : 'sales'}`
            }
          >
            {queue === 'short' ? 'Yêu cầu hỗ trợ' : 'Tất cả đơn'}
            <ArrowRight size={15} />
          </Link>
        }
      >
        <div className="table-toolbar">
          <SearchBox value={query} onChange={setQuery} placeholder="Mã đơn hoặc điểm bán..." />
          <span className="npp-list-total">
            {activeQueue.loading || activeQueue.error ? '—' : activeQueue.rows.length} đơn
          </span>
        </div>
        {!activeQueue.loading && !activeQueue.error && !activeQueue.rows.length ? (
          <div className="npp-clear-state">
            <CheckCheck size={22} />
            <span>Không có đơn trong hàng đợi này</span>
          </div>
        ) : (
          <>
            <div className="npp-order-mobile">
              {visibleOrders.slice(0, 4).map((o) => (
                <article key={`${o.channel}:${o.id}`}>
                  <div>
                    <strong>{o.channel === 'sales' ? storeName(o.storeId) : 'Nhà sản xuất'}</strong>
                    <Badge status={textValue(o.status)} />
                  </div>
                  <Link className="npp-order-code" to={orderLink(o)}>
                    {textValue(o.code)}
                  </Link>
                  <footer>
                    <strong>{money(Number(o.total) || 0)}</strong>
                    <Link className="order-process-button" to={orderLink(o)}>
                      {nextAction[textValue(o.status)] || 'Chi tiết'}
                      <ArrowRight size={14} />
                    </Link>
                  </footer>
                </article>
              ))}
              {!visibleOrders.length && <div className="npp-clear-state">Không có đơn phù hợp</div>}
              {visibleOrders.length > 4 &&
                (queue === 'short' ? (
                  <div className="npp-mobile-exception-links">
                    <Link className="npp-more-orders" to="/orders?channel=sellin&status=short">
                      Đơn nhập chênh lệch
                      <ArrowRight size={15} />
                    </Link>
                    <Link className="npp-more-orders" to="/orders?channel=sales&status=short">
                      Đơn bán giao thiếu
                      <ArrowRight size={15} />
                    </Link>
                  </div>
                ) : (
                  <Link
                    className="npp-more-orders"
                    to={`/orders?channel=${queue === 'receiving' ? 'sellin' : 'sales'}`}
                  >
                    Xem tất cả đơn
                    <ArrowRight size={15} />
                  </Link>
                ))}
            </div>
            <div className="npp-order-desktop">
              <Table<WorkOrder>
                rows={visibleOrders}
                searchText={query}
                columns={[
                  {
                    key: 'code',
                    label: 'Đơn hàng',
                    render: (o) => (
                      <Link
                        className="text-button npp-order-code"
                        title={textValue(o.code)}
                        to={orderLink(o)}
                      >
                        {textValue(o.code)}
                      </Link>
                    ),
                  },
                  {
                    key: 'store',
                    label: queue === 'receiving' ? 'Nguồn hàng' : 'Điểm bán',
                    render: (o) => (o.channel === 'sales' ? storeName(o.storeId) : 'Nhà sản xuất'),
                  },
                  {
                    key: 'date',
                    label: 'Ngày đặt',
                    render: (o) =>
                      o.createdAt
                        ? new Date(textValue(o.createdAt)).toLocaleDateString('vi-VN')
                        : '—',
                  },
                  {
                    key: 'total',
                    label: 'Giá trị đơn',
                    render: (o) => money(Number(o.total) || 0),
                  },
                  {
                    key: 'status',
                    label: 'Trạng thái',
                    render: (o) => <Badge status={textValue(o.status)} />,
                  },
                  {
                    key: 'action',
                    label: 'Xử lý',
                    render: (o) => (
                      <Link className="order-process-button" to={orderLink(o)}>
                        {nextAction[textValue(o.status)] || 'Chi tiết'}
                        <ArrowRight size={14} />
                      </Link>
                    ),
                  },
                ]}
              />
            </div>
          </>
        )}
      </Panel>
      <nav className="npp-action-dock" aria-label="Tác vụ nhanh">
        {has('SALES_CREATE') && (
          <Link to="/catalog?channel=sales">
            <ShoppingBag size={20} />
            <span>Lập đơn điểm bán</span>
            <ArrowRight size={15} />
          </Link>
        )}
        {(has('CRM_WRITE') || has('CATALOG_WRITE')) && (
          <Link to="/references?view=import">
            <FileSpreadsheet size={20} />
            <span>Nhập Excel</span>
            <ArrowRight size={15} />
          </Link>
        )}
        {has('SELLIN_CREATE') && (
          <Link className="npp-assist-action" to="/catalog?assistant=1">
            <WandSparkles size={20} />
            <span>Trợ lý lên đơn</span>
            <ArrowRight size={15} />
          </Link>
        )}
        {has('SELLIN_CREATE') && (
          <Link to="/demand">
            <PackageCheck size={20} />
            <span>Kế hoạch bổ sung hàng</span>
            <ArrowRight size={15} />
          </Link>
        )}
      </nav>
      {(exceptions.length > 0 || followups.length > 0) && (
        <div className="npp-focus-grid">
          {exceptions.length > 0 && (
            <Panel
              title="Giao nhận chênh lệch"
              className="npp-focus-panel tone-attention"
              action={
                <Link className="text-button" to="/tickets">
                  Yêu cầu hỗ trợ
                  <ArrowRight size={15} />
                </Link>
              }
            >
              {exceptions.slice(0, 4).map((o) => (
                <Link className="npp-focus-row" key={`${o.channel}:${o.id}`} to={orderLink(o)}>
                  <span className="npp-focus-icon">
                    <AlertTriangle size={18} />
                  </span>
                  <span>
                    <strong>
                      {o.channel === 'sales' ? storeName(o.storeId) : 'Đơn nhập từ NSX'}
                    </strong>
                    <small>{textValue(o.code)}</small>
                  </span>
                  <Badge status={textValue(o.status)} />
                  <ArrowRight size={15} />
                </Link>
              ))}
              {!exceptions.length && (
                <div className="npp-clear-state">
                  <CheckCheck size={22} />
                  <span>
                    {inbound.loading || outbound.loading
                      ? 'Đang tải...'
                      : inbound.error || outbound.error
                        ? 'Chưa tải được dữ liệu'
                        : 'Không có chênh lệch đang mở'}
                  </span>
                </div>
              )}
            </Panel>
          )}
          {followups.length > 0 && (
            <Panel
              title="Phản hồi điểm bán"
              className="npp-focus-panel tone-care"
              action={
                <Link className="text-button" to="/npp-workflow">
                  {replacements.loading || replacements.error ? '—' : followups.length} chờ phản hồi
                  <ArrowRight size={15} />
                </Link>
              }
            >
              <LoadState {...replacements} retry={replacements.reload} />
              {followups.slice(0, 4).map((r) => (
                <Link
                  className="npp-focus-row"
                  key={textValue(r.id)}
                  to={`/npp-workflow?replacement=${r.id}`}
                >
                  <span className="npp-focus-icon">
                    <Clock3 size={18} />
                  </span>
                  <span>
                    <strong>{storeName(r.storeId)}</strong>
                    <small>
                      GB-{textValue(r.id)} ·{' '}
                      {new Date(textValue(r.dueAt)).toLocaleString('vi-VN', {
                        hour: '2-digit',
                        minute: '2-digit',
                        day: '2-digit',
                        month: '2-digit',
                      })}
                    </small>
                  </span>
                  <Badge
                    status={
                      new Date(textValue(r.dueAt)).getTime() < Date.now()
                        ? 'Quá hạn'
                        : 'Chờ phản hồi'
                    }
                  />
                  <ArrowRight size={15} />
                </Link>
              ))}
              {!replacements.loading && !replacements.error && !followups.length && (
                <div className="npp-clear-state">
                  <CheckCheck size={22} />
                  <span>Không có phản hồi đang chờ</span>
                </div>
              )}
            </Panel>
          )}
        </div>
      )}
    </div>
  );
}
