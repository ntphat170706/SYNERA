import { Link } from 'react-router-dom';
import { ArrowRight, Building2 } from 'lucide-react';
import { numberValue, record, textValue, type RecordData } from '../../shared/api';
import { Badge, Table } from '../../shared/ui';
import { money } from '../../shared/utils';
import { rows, personName, nppName, dateText, organizationKinds as kinds } from './model';
export function withinOrg(node: RecordData, id: string, nodes: RecordData[]) {
  let current: RecordData | undefined = node;
  const visited = new Set<unknown>();
  while (current && !visited.has(current.id)) {
    if (String(current.id) === id) return true;
    visited.add(current.id);
    current = nodes.find((o) => o.id === current?.parentId);
  }
  return false;
}
export function OrgTree({
  nodes,
  selected,
  onSelect,
}: {
  nodes: RecordData[];
  selected: string;
  onSelect: (id: string) => void;
}) {
  const render = (list: RecordData[], seen: Set<unknown>): React.ReactNode => (
    <ul className="nsx-org-tree">
      {list
        .filter((o) => !seen.has(o.id))
        .map((o) => {
          const visited = new Set(seen).add(o.id),
            children = nodes.filter((n) => n.parentId === o.id);
          return (
            <li key={textValue(o.id)}>
              <button
                aria-pressed={selected === String(o.id)}
                onClick={() => onSelect(selected === String(o.id) ? '' : String(o.id))}
              >
                <Building2 size={16} />
                <span>
                  {textValue(o.name)}
                  <small>{kinds[textValue(o.type)] || 'Đơn vị bán hàng'}</small>
                </span>
              </button>
              {children.length > 0 && render(children, visited)}
            </li>
          );
        })}
    </ul>
  );
  return render(
    nodes.filter((o) => !nodes.some((n) => n.id === o.parentId)),
    new Set(),
  );
}
export function ShortOrderRows({ list, data }: { list: RecordData[]; data: RecordData }) {
  const pending = list.filter(
    (o) => !rows(data, 'replacements').some((r) => r.orderId === o.id && r.status !== 'CANCELLED'),
  );
  if (!pending.length) return null;
  return (
    <Table
      rows={pending}
      columns={[
        {
          key: 'code',
          label: 'Đơn giao thiếu',
          render: (o) => (
            <Link to={`/orders?channel=sales&order=${o.id}`}>{textValue(o.code)}</Link>
          ),
        },
        { key: 'nppId', label: 'NPP xử lý', render: (o) => nppName(data, o.nppId) },
        { key: 'repId', label: 'Sales phụ trách', render: (o) => personName(data, o.repId) },
        {
          key: 'next',
          label: 'Tiếp theo',
          render: () => <span className="nsx-text-attention">Chờ NPP phản hồi phương án</span>,
        },
      ]}
    />
  );
}
export function DetailGrid({ items }: { items: [string, unknown][] }) {
  return (
    <dl className="nsx-detail-grid">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value == null || value === '' ? 'Chưa cập nhật' : (value as React.ReactNode)}</dd>
        </div>
      ))}
    </dl>
  );
}
export function DataStamp({ data }: { data: RecordData }) {
  return data.generatedAt ? (
    <footer className="nsx-data-stamp">
      <span>
        {textValue(record(data.manufacturer).name)} · {dateText(data.from)} – {dateText(data.to)}
      </span>
      <time>Cập nhật {new Date(textValue(data.generatedAt)).toLocaleString('vi-VN')}</time>
    </footer>
  ) : null;
}
export function OrderRows({ list, channel }: { list: RecordData[]; channel: string }) {
  return (
    <Table
      rows={list}
      columns={[
        {
          key: 'code',
          label: 'Mã đơn',
          render: (r) => (
            <Link to={`/orders?channel=${channel}&order=${r.id}`}>{textValue(r.code)}</Link>
          ),
        },
        { key: 'date', label: 'Ngày tạo', render: (r) => dateText(r.createdAt) },
        {
          key: 'status',
          label: 'Trạng thái',
          render: (r) => <Badge status={textValue(r.status)} />,
        },
        { key: 'total', label: 'Giá trị đơn', render: (r) => money(numberValue(r.total)) },
        {
          key: 'action',
          label: 'Thao tác',
          render: (r) => (
            <Link className="text-button" to={`/orders?channel=${channel}&order=${r.id}`}>
              Mở đơn <ArrowRight size={14} />
            </Link>
          ),
        },
      ]}
    />
  );
}
export function CareRows({ list, data }: { list: RecordData[]; data: RecordData }) {
  if (!list.length)
    return <div className="nsx-compact-empty">Không có giao bù đang chờ xử lý.</div>;
  return (
    <Table
      rows={list}
      columns={[
        {
          key: 'id',
          label: 'Giao bù',
          render: (r) => <Link to={`/npp-workflow?replacement=${r.id}`}>GB-{textValue(r.id)}</Link>,
        },
        {
          key: 'status',
          label: 'Tiến độ hàng',
          render: (r) => <Badge status={textValue(r.status)} />,
        },
        { key: 'repId', label: 'Sales phụ trách', render: (r) => personName(data, r.repId) },
        { key: 'dueAt', label: 'Hạn xử lý', render: (r) => dateText(r.dueAt) },
        {
          key: 'customer',
          label: 'Chăm sóc khách',
          render: (r) => (
            <span className={r.notifiedAt ? 'nsx-text-done' : 'nsx-text-care'}>
              {r.notifiedAt ? 'Đã báo khách' : 'Chờ báo lại khách'}
            </span>
          ),
        },
      ]}
    />
  );
}
export function AppointmentRows({ list, data }: { list: RecordData[]; data: RecordData }) {
  if (!list.length)
    return <div className="nsx-compact-empty">Không có lịch hẹn trong danh sách này.</div>;
  return (
    <Table
      rows={list}
      columns={[
        { key: 'title', label: 'Nội dung hẹn', render: (r) => textValue(r.title) },
        { key: 'repId', label: 'Sales phụ trách', render: (r) => personName(data, r.repId) },
        { key: 'dueAt', label: 'Thời hạn', render: (r) => dateText(r.dueAt) },
        {
          key: 'status',
          label: 'Trạng thái',
          render: (r) => <Badge status={textValue(r.status)} />,
        },
      ]}
    />
  );
}
