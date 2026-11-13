import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ShoppingBag,
  PackageCheck,
  MessageSquareText,
  CalendarClock,
} from 'lucide-react';
import { useApi } from '../../shared/hooks/useApi';
import { numberValue, textValue, type RecordData } from '../../shared/api';
import { LoadState } from '../../shared/ApiUI';
import { Badge, PageHead, Table } from '../../shared/ui';
import { money } from '../../shared/utils';
import {
  rows,
  nppName,
  personName,
  storeName,
  regionName,
  statsFor,
  isOpenCase,
  dateText,
} from './model';
import { DataStamp, ShortOrderRows } from './RecordParts';
export default function ControlRoom() {
  const result = useApi<RecordData>('/manufacturer/network', {}),
    data = result.data;
  const approvals = rows(data, 'pendingSellIn');
  const cases = rows(data, 'replacements').filter(isOpenCase),
    appointments = rows(data, 'appointments').filter((a) => a.status === 'OPEN');
  const now = Date.now(),
    late = appointments.filter((a) => new Date(textValue(a.dueAt)).getTime() < now);
  if (!data.generatedAt)
    return (
      <>
        <PageHead title="Điều hành hôm nay" />
        <LoadState {...result} retry={result.reload} />
      </>
    );
  return (
    <div className="nsx-command-center">
      <div className="nsx-breadcrumb">
        <span>Điều hành mạng lưới</span>
        <span>/</span>
        <strong>Hôm nay</strong>
      </div>
      <PageHead title="Điều hành hôm nay">
        <Link className="btn" to="/network">
          Mở mạng lưới <ArrowRight size={16} />
        </Link>
      </PageHead>
      <LoadState {...result} retry={result.reload} />
      <nav className="nsx-work-strip" aria-label="Việc cần xử lý">
        <Link to="/orders?channel=sellin&status=pending">
          <ShoppingBag size={20} />
          <span>
            Đơn nhập chờ xử lý<strong>{approvals.length}</strong>
          </span>
          <ArrowRight size={17} />
        </Link>
        <Link to="/npp-workflow">
          <PackageCheck size={20} />
          <span>
            Giao bù đang theo dõi<strong>{cases.length}</strong>
          </span>
          <ArrowRight size={17} />
        </Link>
        <Link to="/network?view=stores&work=contact">
          <MessageSquareText size={20} />
          <span>
            Điểm bán chờ báo lại
            <strong>
              {new Set(cases.filter((r) => !r.notifiedAt).map((r) => r.storeId)).size}
            </strong>
          </span>
          <ArrowRight size={17} />
        </Link>
        <Link to="/network?view=stores&work=overdue">
          <CalendarClock size={20} />
          <span>
            Điểm bán có hẹn quá hạn<strong>{new Set(late.map((a) => a.storeId)).size}</strong>
          </span>
          <ArrowRight size={17} />
        </Link>
      </nav>
      <section className="nsx-data-surface">
        <header className="nsx-work-head">
          <h2>Đơn nhập cần hãng xác nhận</h2>
          <Link to="/orders?channel=sellin&status=pending">
            Xem tất cả <ArrowRight size={14} />
          </Link>
        </header>
        <Table
          rows={approvals}
          columns={[
            {
              key: 'code',
              label: 'Đơn nhập',
              render: (r) => (
                <Link to={`/orders?channel=sellin&order=${r.id}`}>{textValue(r.code)}</Link>
              ),
            },
            {
              key: 'nppId',
              label: 'Nhà phân phối',
              render: (r) => (
                <Link to={`/network?view=npps&id=${r.nppId}`}>{nppName(data, r.nppId)}</Link>
              ),
            },
            { key: 'createdAt', label: 'Ngày đặt', render: (r) => dateText(r.createdAt) },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r) => <Badge status={textValue(r.status)} />,
            },
            { key: 'total', label: 'Giá trị đơn', render: (r) => money(numberValue(r.total)) },
            {
              key: 'action',
              label: 'Xử lý',
              render: (r) => (
                <Link className="btn nsx-row-action" to={`/orders?channel=sellin&order=${r.id}`}>
                  Xử lý đơn <ArrowRight size={14} />
                </Link>
              ),
            },
          ]}
        />
      </section>
      <section className="nsx-data-surface">
        <header className="nsx-work-head">
          <h2>Tình hình hàng theo nhà phân phối</h2>
          <Link to="/reports">
            Mở báo cáo <ArrowRight size={14} />
          </Link>
        </header>
        <Table
          rows={rows(data, 'npps')}
          columns={[
            {
              key: 'name',
              label: 'Nhà phân phối',
              render: (r) => <Link to={`/network?view=npps&id=${r.id}`}>{textValue(r.name)}</Link>,
            },
            { key: 'region', label: 'Vùng', render: (r) => regionName(r.region) },
            {
              key: 'inbound',
              label: 'Giá trị đơn nhập',
              render: (r) => money(statsFor(data, r.id).inbound),
            },
            {
              key: 'outbound',
              label: 'Giá trị đơn bán',
              render: (r) => money(statsFor(data, r.id).outbound),
            },
            { key: 'stock', label: 'SKU khả dụng', render: (r) => statsFor(data, r.id).activeSku },
            { key: 'stores', label: 'Điểm bán', render: (r) => statsFor(data, r.id).stores },
            {
              key: 'action',
              label: 'Truy vết',
              render: (r) => (
                <Link to={`/network?view=npps&id=${r.id}`}>
                  Hồ sơ 360° <ArrowRight size={14} />
                </Link>
              ),
            },
          ]}
        />
        <div className="nsx-report-source">
          <span>
            {dateText(data.from)} – {dateText(data.to)}
          </span>
          <span>Tồn khả dụng hiện tại · Giá trị đơn tạo trong kỳ</span>
        </div>
      </section>
      <section className="nsx-data-surface">
        <header className="nsx-work-head">
          <h2>Đang chờ NPP & Sales cập nhật kết quả</h2>
          <Link to="/npp-workflow">
            Trung tâm phối hợp <ArrowRight size={14} />
          </Link>
        </header>
        <ShortOrderRows list={rows(data, 'openSalesOrders')} data={data} />
        {cases.length ? (
          <Table
            rows={cases}
            columns={[
              {
                key: 'storeId',
                label: 'Điểm bán',
                render: (r) => (
                  <Link to={`/network?view=stores&id=${r.storeId}`}>
                    {storeName(data, r.storeId)}
                  </Link>
                ),
              },
              { key: 'nppId', label: 'NPP xử lý hàng', render: (r) => nppName(data, r.nppId) },
              { key: 'repId', label: 'Sales phụ trách', render: (r) => personName(data, r.repId) },
              {
                key: 'status',
                label: 'Tiến độ giao bù',
                render: (r) => <Badge status={textValue(r.status)} />,
              },
              {
                key: 'care',
                label: 'Kết quả chăm sóc',
                render: (r) => (
                  <span className={r.notifiedAt ? 'nsx-text-done' : 'nsx-text-care'}>
                    {r.notifiedAt ? 'Đã báo khách' : 'Chờ báo lại khách'}
                  </span>
                ),
              },
              { key: 'dueAt', label: 'Hạn', render: (r) => dateText(r.dueAt) },
              {
                key: 'action',
                label: 'Theo dõi',
                render: (r) => (
                  <Link to={`/orders?channel=sales&order=${r.orderId}`}>
                    Mở đơn gốc <ArrowRight size={14} />
                  </Link>
                ),
              },
            ]}
          />
        ) : (
          <div className="nsx-compact-empty">Không có giao bù đang chờ cập nhật.</div>
        )}
      </section>
      <DataStamp data={data} />
    </div>
  );
}
