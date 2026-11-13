import { organizationKinds as kinds } from './model';
import {
  withinOrg,
  OrgTree,
  ShortOrderRows,
  DetailGrid,
  DataStamp,
  OrderRows,
  CareRows,
  AppointmentRows,
} from './RecordParts';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  MapPin,
  Plus,
  CalendarDays,
  Check,
  Phone,
} from 'lucide-react';
import { useApi } from '../../shared/hooks/useApi';
import { useStore } from '../../shared/store';
import {
  command,
  numberValue,
  records,
  record,
  textValue,
  type RecordData,
} from '../../shared/api';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { Badge, PageHead, SearchBox, Table } from '../../shared/ui';
import { money, today } from '../../shared/utils';
import { unitName } from '../../shared/productMedia';
import {
  rows,
  regionName,
  personName,
  nppName,
  roleName,
  statsFor,
  isOpenCase,
  dateText,
} from './model';

const views = [
  ['npps', 'Nhà phân phối'],
  ['stores', 'Điểm bán'],
  ['organization', 'Tổ chức & vùng'],
  ['routes', 'Tuyến bán hàng'],
];

export default function Network({ initialView = 'npps' }: { initialView?: string }) {
  const [params, setParams] = useSearchParams(),
    { has, notify, refresh } = useStore();
  const view = params.get('view') || initialView,
    id = params.get('id') || params.get('store') || '';
  const result = useApi<RecordData>('/manufacturer/network', {}),
    data = result.data;
  const [query, setQuery] = useState(''),
    [tab, setTab] = useState('details'),
    [action, setAction] = useState('');
  const [selectedOrg, setSelectedOrg] = useState('');
  const [picked, setPicked] = useState<RecordData>({}),
    [npp, setNpp] = useState('');
  const npps = rows(data, 'npps'),
    stores = rows(data, 'stores'),
    routes = rows(data, 'routes'),
    staff = rows(data, 'staff');
  const work = params.get('work');
  const visibleStores =
    work === 'contact'
      ? stores.filter((s) =>
          rows(data, 'replacements').some(
            (r) => r.storeId === s.id && isOpenCase(r) && !r.notifiedAt,
          ),
        )
      : work === 'overdue'
        ? stores.filter((s) =>
            rows(data, 'appointments').some(
              (a) =>
                a.storeId === s.id &&
                a.status === 'OPEN' &&
                new Date(textValue(a.dueAt)).getTime() < Date.now(),
            ),
          )
        : stores;
  const nppId = npp || String(npps[0]?.id || '');
  const entity =
    (view === 'stores' ? stores : view === 'routes' ? routes : npps).find(
      (r) => String(r.id) === id,
    ) || {};
  const profile = useApi<RecordData>(
    view === 'npps' && id ? `/manufacturer/npps/${id}/360` : null,
    {},
  );
  const storeProfile = useApi<RecordData>(view === 'stores' && id ? `/stores/${id}/360` : null, {});
  const section = useApi<RecordData>(
    view === 'stores' && id && tab !== 'details'
      ? `/stores/${id}/360/${tab}?page=${params.get('page') || 0}&size=10`
      : null,
    {},
  );
  const select = (row: RecordData) => {
    setTab('details');
    setParams({ view, id: String(row.id) });
  };
  const open = (name: string, row: RecordData = {}) => {
    setAction(name);
    setPicked(row);
  };
  const people = staff.filter(
    (s) => String(s.nppId) === nppId && ['SALES', 'SUPERVISOR'].includes(textValue(s.role)),
  );
  const fields: FormField[] =
    action === 'route'
      ? [
          { key: 'name', label: 'Tên tuyến bán hàng' },
          {
            key: 'repId',
            label: 'Sales phụ trách',
            options: people.map((p) => ({ value: textValue(p.id), label: textValue(p.name) })),
          },
          {
            key: 'storeIds',
            label: 'Điểm bán trong tuyến',
            type: 'multiselect',
            options: stores
              .filter((s) => String(s.nppId) === nppId)
              .map((s) => ({ value: textValue(s.id), label: textValue(s.name) })),
          },
          {
            key: 'weekday',
            label: 'Ngày ghé trong tuần',
            options: [1, 2, 3, 4, 5, 6, 7].map((d) => ({
              value: d,
              label: d === 7 ? 'Chủ nhật' : `Thứ ${d + 1}`,
            })),
          },
        ]
      : action === 'plan'
        ? [{ key: 'day', label: 'Ngày ghé thăm', type: 'date', value: today() }]
        : action === 'handover'
          ? [
              {
                key: 'fromRepId',
                label: 'Sales bàn giao',
                options: people.map((p) => ({ value: textValue(p.id), label: textValue(p.name) })),
              },
              {
                key: 'toRepId',
                label: 'Sales tiếp nhận',
                options: people.map((p) => ({ value: textValue(p.id), label: textValue(p.name) })),
              },
              { key: 'reason', label: 'Ghi chú', type: 'textarea' },
            ]
          : [
              { key: 'code', label: 'Mã tổ chức', value: textValue(picked.code) },
              { key: 'name', label: 'Tên tổ chức', value: textValue(picked.name) },
              {
                key: 'type',
                label: 'Loại tổ chức',
                value: textValue(picked.type) || 'REGION',
                options: Object.entries(kinds)
                  .filter(([v]) =>
                    picked.type === 'NPP' ? v === 'NPP' : v !== 'NPP' && v !== 'NSX',
                  )
                  .map(([value, label]) => ({ value, label })),
              },
              { key: 'region', label: 'Mã vùng', value: textValue(picked.region) || 'SOUTH' },
              {
                key: 'parentId',
                label: 'Tổ chức cấp trên',
                value: numberValue(picked.parentId) || numberValue(record(data.manufacturer).id),
                options: rows(data, 'organizations')
                  .filter((o) => o.type !== 'NPP' && o.id !== picked.id)
                  .map((o) => ({ value: textValue(o.id), label: textValue(o.name) })),
              },
              { key: 'reason', label: 'Ghi chú', type: 'textarea' },
            ];
  async function submit(values: Record<string, string>) {
    if (action === 'npp')
      await command('/organizations', { ...values, creditLimit: Number(values.creditLimit) });
    else if (action === 'store')
      await command('/stores', {
        ...values,
        nppId: Number(nppId),
        repId: Number(values.repId),
        latitude: null,
        longitude: null,
      });
    else if (action === 'route')
      await command('/routes', {
        ...values,
        nppId: Number(nppId),
        repId: Number(values.repId),
        weekday: Number(values.weekday),
        storeIds: values.storeIds.split(',').map(Number),
      });
    else if (action === 'plan') await command(`/routes/${picked.id || id}/plan`, values);
    else if (action === 'handover')
      await command('/handovers', {
        ...values,
        nppId: Number(nppId),
        fromRepId: Number(values.fromRepId),
        toRepId: Number(values.toRepId),
      });
    else
      await command(picked.id ? `/organizations/${picked.id}` : '/organizations/nodes', {
        ...values,
        parentId: Number(values.parentId) || null,
        version: picked.version ?? null,
      });
    result.reload();
    profile.reload();
    await refresh();
    notify('Đã lưu thay đổi mạng lưới.');
  }
  const filter = (list: RecordData[]) =>
    list.filter((r) =>
      JSON.stringify(r).toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')),
    );
  const titles = Object.fromEntries(views);
  const tabs =
    view === 'npps'
      ? [
          ['details', 'Chi tiết'],
          ['orders', 'Đơn hàng'],
          ['stock', 'Kho hàng'],
          ['stores', 'Điểm bán'],
          ['routes', 'Tuyến & Sales'],
          ['care', 'Chăm sóc'],
        ]
      : view === 'stores'
        ? [
            ['details', 'Chi tiết'],
            ['orders', 'Đơn hàng'],
            ['interactions', 'Tương tác'],
            ['appointments', 'Lịch hẹn'],
            ['tickets', 'Phản ánh'],
            ['display', 'Trưng bày'],
          ]
        : [
            ['details', 'Thông tin chung'],
            ['schedule', 'Thiết lập MCP'],
          ];
  const schedule = records(entity.stops),
    own = profile.data;
  if (result.error && !data.generatedAt)
    return (
      <>
        <PageHead title="Mạng lưới phân phối" />
        <LoadState {...result} retry={result.reload} />
      </>
    );
  if (!result.loading && data.generatedAt && id && !entity.id)
    return (
      <>
        <PageHead title="Không tìm thấy hồ sơ" />
        <p role="alert">Hồ sơ không có trong phạm vi được cấp.</p>
        <button className="btn" onClick={() => setParams({ view })}>
          Về danh sách
        </button>
      </>
    );
  return (
    <div className="nsx-network">
      <div className="nsx-breadcrumb">
        <Link to="/network">Mạng lưới phân phối</Link>
        <span>/</span>
        <button onClick={() => setParams({ view })}>{titles[view]}</button>
        {id && (
          <>
            <span>/</span>
            <span>{textValue(entity.name) || 'Hồ sơ 360°'}</span>
          </>
        )}
      </div>
      <LoadState {...result} retry={result.reload} />
      {!id ? (
        <>
          <PageHead title="Mạng lưới phân phối">
            {view === 'npps' && has('USER_MANAGE') && (
              <button className="btn primary" onClick={() => open('npp')}>
                <Plus size={16} />
                Thêm nhà phân phối
              </button>
            )}
            {view === 'stores' && has('CRM_WRITE') && (
              <button className="btn primary" onClick={() => open('store')}>
                <Plus size={16} />
                Thêm điểm bán
              </button>
            )}
            {view === 'stores' && has('CRM_WRITE') && (
              <Link className="btn" to="/references?view=import">
                Nhập điểm bán từ Excel
              </Link>
            )}
            {view === 'organization' && has('USER_MANAGE') && (
              <button className="btn primary" onClick={() => open('organization')}>
                <Plus size={16} />
                Thêm tổ chức
              </button>
            )}
            {view === 'routes' && has('ROUTE_WRITE') && (
              <>
                <button className="btn" onClick={() => open('handover')}>
                  Bàn giao Sales
                </button>
                <button className="btn primary" onClick={() => open('route')}>
                  <Plus size={16} />
                  Thêm tuyến bán hàng
                </button>
              </>
            )}
          </PageHead>
          <nav className="nsx-tabs" aria-label="Mạng lưới phân phối">
            {views.map(([v, name]) => (
              <button
                key={v}
                className={view === v ? 'active' : ''}
                onClick={() => {
                  setQuery('');
                  setParams({ view: v });
                }}
              >
                {name}
              </button>
            ))}
          </nav>
          <section className="nsx-data-surface">
            <div className="nsx-list-tools">
              <SearchBox
                value={query}
                onChange={setQuery}
                placeholder={`Tìm ${titles[view]?.toLocaleLowerCase('vi')}…`}
              />
              {view === 'routes' && (
                <label>
                  NPP phục vụ
                  <select value={nppId} onChange={(e) => setNpp(e.target.value)}>
                    {npps.map((n) => (
                      <option key={textValue(n.id)} value={textValue(n.id)}>
                        {textValue(n.name)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>
            {view === 'npps' && (
              <Table
                rows={filter(npps)}
                onRow={select}
                columns={[
                  {
                    key: 'name',
                    label: 'Nhà phân phối',
                    render: (r) => (
                      <div className="nsx-entity-name">
                        <Building2 size={19} />
                        <span>
                          <strong>{textValue(r.name)}</strong>
                          <small>{textValue(r.code)}</small>
                        </span>
                      </div>
                    ),
                  },
                  { key: 'region', label: 'Vùng bán hàng', render: (r) => regionName(r.region) },
                  { key: 'stores', label: 'Điểm bán', render: (r) => statsFor(data, r.id).stores },
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
                  {
                    key: 'stock',
                    label: 'SKU khả dụng',
                    render: (r) => statsFor(data, r.id).activeSku,
                  },
                  {
                    key: 'action',
                    label: 'Hồ sơ',
                    render: (r) => (
                      <button className="text-button" onClick={() => select(r)}>
                        Xem 360° <ArrowRight size={14} />
                      </button>
                    ),
                  },
                ]}
              />
            )}
            {view === 'stores' && work && (
              <div className="nsx-filter-context">
                <strong>
                  {work === 'contact'
                    ? 'Điểm bán đang chờ báo lại'
                    : 'Điểm bán có lịch hẹn quá hạn'}
                </strong>
                <button className="text-button" onClick={() => setParams({ view })}>
                  Xóa bộ lọc
                </button>
              </div>
            )}
            {view === 'stores' && (
              <Table
                rows={filter(visibleStores)}
                onRow={select}
                columns={[
                  {
                    key: 'name',
                    label: 'Điểm bán',
                    render: (r) => (
                      <div className="nsx-entity-name">
                        <span className="nsx-letter-avatar">{textValue(r.name).slice(0, 1)}</span>
                        <span>
                          <strong>{textValue(r.name)}</strong>
                          <small>{textValue(r.address)}</small>
                        </span>
                      </div>
                    ),
                  },
                  {
                    key: 'owner',
                    label: 'Người liên hệ',
                    render: (r) => (
                      <div>
                        {textValue(r.owner)}
                        <small className="nsx-subline">{textValue(r.phone)}</small>
                      </div>
                    ),
                  },
                  { key: 'nppId', label: 'NPP phục vụ', render: (r) => nppName(data, r.nppId) },
                  {
                    key: 'repId',
                    label: 'Sales phụ trách',
                    render: (r) => personName(data, r.repId),
                  },
                  {
                    key: 'status',
                    label: 'Trạng thái',
                    render: (r) => <Badge status={textValue(r.status)} />,
                  },
                  {
                    key: 'action',
                    label: 'Hồ sơ',
                    render: (r) => (
                      <button className="text-button" onClick={() => select(r)}>
                        Xem 360° <ArrowRight size={14} />
                      </button>
                    ),
                  },
                ]}
              />
            )}
            {view === 'routes' && (
              <Table
                rows={filter(routes.filter((r) => String(r.nppId) === nppId))}
                onRow={select}
                columns={[
                  {
                    key: 'name',
                    label: 'Tuyến bán hàng',
                    render: (r) => <strong>{textValue(r.name)}</strong>,
                  },
                  { key: 'nppId', label: 'NPP phục vụ', render: (r) => nppName(data, r.nppId) },
                  {
                    key: 'repId',
                    label: 'Sales phụ trách',
                    render: (r) => personName(data, r.repId),
                  },
                  { key: 'stops', label: 'Điểm bán', render: (r) => records(r.stops).length },
                  {
                    key: 'days',
                    label: 'Ngày ghé',
                    render: (r) =>
                      [...new Set(records(r.stops).map((s) => numberValue(s.weekday)))]
                        .sort()
                        .map((d) => (d === 7 ? 'CN' : `T${d + 1}`))
                        .join(' · '),
                  },
                  {
                    key: 'action',
                    label: 'Thiết lập',
                    render: (r) => (
                      <button className="text-button" onClick={() => select(r)}>
                        Mở tuyến <ArrowRight size={14} />
                      </button>
                    ),
                  },
                ]}
              />
            )}
            {view === 'organization' && (
              <div className="nsx-org-workspace">
                <aside>
                  <h3>Cây tổ chức bán hàng</h3>
                  <OrgTree
                    nodes={rows(data, 'organizations')}
                    selected={selectedOrg}
                    onSelect={setSelectedOrg}
                  />
                </aside>
                <Table
                  rows={filter(
                    rows(data, 'organizations').filter(
                      (o) => !selectedOrg || withinOrg(o, selectedOrg, rows(data, 'organizations')),
                    ),
                  )}
                  columns={[
                    {
                      key: 'name',
                      label: 'Tên tổ chức',
                      render: (r) => <strong>{textValue(r.name)}</strong>,
                    },
                    {
                      key: 'type',
                      label: 'Loại tổ chức',
                      render: (r) => kinds[textValue(r.type)] || 'Đơn vị bán hàng',
                    },
                    {
                      key: 'parent',
                      label: 'Trực thuộc',
                      render: (r) =>
                        textValue(
                          rows(data, 'organizations').find((o) => o.id === r.parentId)?.name,
                        ) || '—',
                    },
                    { key: 'region', label: 'Vùng bán hàng', render: (r) => regionName(r.region) },
                    {
                      key: 'action',
                      label: 'Thao tác',
                      render: (r) =>
                        has('USER_MANAGE') && r.type !== 'NSX' ? (
                          <button className="text-button" onClick={() => open('organization', r)}>
                            Chỉnh sửa
                          </button>
                        ) : r.type === 'NPP' ? (
                          <Link to={`/network?view=npps&id=${r.id}`}>Hồ sơ NPP</Link>
                        ) : null,
                    },
                  ]}
                />
              </div>
            )}
          </section>
          <DataStamp data={data} />
        </>
      ) : (
        <>
          <button
            className="text-button nsx-back"
            onClick={() => {
              setParams({ view });
              setTab('details');
            }}
          >
            <ArrowLeft size={15} />
            Danh sách {titles[view]?.toLocaleLowerCase('vi')}
          </button>
          <header className="nsx-record-head">
            <span className="nsx-record-avatar">
              {view === 'stores' ? (
                <MapPin size={28} />
              ) : view === 'routes' ? (
                <CalendarDays size={28} />
              ) : (
                <Building2 size={28} />
              )}
            </span>
            <div>
              <h1>{textValue(entity.name) || 'Đang tải hồ sơ'}</h1>
              <p>
                {view === 'stores'
                  ? textValue(entity.address)
                  : view === 'routes'
                    ? nppName(data, entity.nppId)
                    : regionName(entity.region)}
              </p>
              <span className="nsx-soft-label">
                {view === 'stores'
                  ? 'Điểm bán'
                  : view === 'routes'
                    ? 'Tuyến bán hàng'
                    : textValue(entity.code)}
              </span>
            </div>
            <div className="nsx-record-actions">
              {view === 'routes' && has('ROUTE_WRITE') && (
                <button className="btn primary" onClick={() => open('plan', entity)}>
                  Lập lịch ghé
                </button>
              )}
              {view === 'stores' && (
                <a className="btn" href={`tel:${textValue(entity.phone)}`}>
                  <Phone size={15} />
                  Liên hệ
                </a>
              )}
            </div>
          </header>
          <nav className="nsx-tabs" aria-label="Các phần hồ sơ">
            {tabs.map(([v, name]) => (
              <button
                className={tab === v ? 'active' : ''}
                key={v}
                onClick={() => {
                  setTab(v);
                  params.delete('page');
                  setParams(params);
                }}
              >
                {name}
              </button>
            ))}
          </nav>
          <section className="nsx-record-body">
            {view === 'npps' && (
              <>
                <LoadState {...profile} retry={profile.reload} />
                {tab === 'details' && (
                  <>
                    <h2>Thông tin nhà phân phối</h2>
                    <DetailGrid
                      items={[
                        ['Tên nhà phân phối', entity.name],
                        ['Mã NPP', entity.code],
                        ['Vùng bán hàng', regionName(entity.region)],
                        ['Nhãn hàng', record(data.manufacturer).name],
                        [
                          'Đầu mối NPP',
                          staff
                            .filter((s) => s.nppId === entity.id && s.role === 'NPP_OWNER')
                            .map((s) => textValue(s.name))
                            .join(', '),
                        ],
                        [
                          'Sales phụ trách',
                          staff
                            .filter((s) => s.nppId === entity.id && s.role === 'SALES')
                            .map((s) => textValue(s.name))
                            .join(', '),
                        ],
                        ['Điểm bán phục vụ', rows(own, 'stores').length],
                        ['SKU khả dụng', statsFor(data, id).activeSku],
                      ]}
                    />
                    <div className="nsx-profile-values">
                      <span>
                        Đơn nhập<strong>{money(statsFor(data, id).inbound)}</strong>
                      </span>
                      <span>
                        Đơn bán điểm bán<strong>{money(statsFor(data, id).outbound)}</strong>
                      </span>
                      <span>
                        Doanh số thực nhận<strong>{money(numberValue(own.receivedSales))}</strong>
                      </span>
                    </div>
                  </>
                )}
                {tab === 'orders' && (
                  <>
                    <h2>Đơn nhập từ hãng</h2>
                    <OrderRows list={rows(own, 'sellIn')} channel="sellin" />
                    <h2>Đơn bán điểm bán</h2>
                    <OrderRows list={rows(own, 'sellOut')} channel="sales" />
                  </>
                )}
                {tab === 'stock' && (
                  <>
                    <h2>Tồn kho theo sản phẩm & lô</h2>
                    <Table
                      rows={rows(own, 'stock')}
                      columns={[
                        {
                          key: 'skuName',
                          label: 'Sản phẩm',
                          render: (r) => <strong>{textValue(r.skuName)}</strong>,
                        },
                        {
                          key: 'warehouseName',
                          label: 'Kho',
                          render: (r) => textValue(r.warehouseName),
                        },
                        { key: 'lotNo', label: 'Lô hàng', render: (r) => textValue(r.lotNo) },
                        { key: 'onHand', label: 'Tồn thực', render: (r) => textValue(r.onHand) },
                        { key: 'reserved', label: 'Đã giữ', render: (r) => textValue(r.reserved) },
                        {
                          key: 'available',
                          label: 'Khả dụng',
                          render: (r) => <strong>{textValue(r.available)}</strong>,
                        },
                        {
                          key: 'unit',
                          label: 'Đơn vị',
                          render: (r) => unitName(textValue(r.baseUnit)),
                        },
                        {
                          key: 'expiryDate',
                          label: 'Hạn sử dụng',
                          render: (r) => dateText(r.expiryDate),
                        },
                      ]}
                    />
                  </>
                )}
                {tab === 'stores' && (
                  <>
                    <h2>Điểm bán được phục vụ</h2>
                    <Table
                      rows={rows(own, 'stores')}
                      columns={[
                        {
                          key: 'name',
                          label: 'Điểm bán',
                          render: (r) => (
                            <Link
                              to={`/network?view=stores&id=${r.id}`}
                              onClick={() => setTab('details')}
                            >
                              {textValue(r.name)}
                            </Link>
                          ),
                        },
                        { key: 'address', label: 'Địa chỉ', render: (r) => textValue(r.address) },
                        {
                          key: 'repId',
                          label: 'Sales phụ trách',
                          render: (r) => personName(data, r.repId),
                        },
                        {
                          key: 'status',
                          label: 'Trạng thái',
                          render: (r) => <Badge status={textValue(r.status)} />,
                        },
                      ]}
                    />
                  </>
                )}
                {tab === 'routes' && (
                  <>
                    <h2>Tuyến bán hàng</h2>
                    <Table
                      rows={rows(own, 'routes')}
                      columns={[
                        {
                          key: 'name',
                          label: 'Tuyến',
                          render: (r) => (
                            <Link
                              to={`/network?view=routes&id=${r.id}`}
                              onClick={() => setTab('details')}
                            >
                              {textValue(r.name)}
                            </Link>
                          ),
                        },
                        {
                          key: 'repId',
                          label: 'Phụ trách',
                          render: (r) => personName(data, r.repId),
                        },
                        { key: 'stops', label: 'Điểm bán', render: (r) => records(r.stops).length },
                      ]}
                    />
                    <h2>Đội ngũ phối hợp</h2>
                    <Table
                      rows={rows(own, 'staff')}
                      columns={[
                        { key: 'name', label: 'Họ tên', render: (r) => textValue(r.name) },
                        { key: 'role', label: 'Vai trò', render: (r) => roleName(r.role) },
                      ]}
                    />
                  </>
                )}
                {tab === 'care' && (
                  <>
                    <h2>Giao nhận & chăm sóc đang mở</h2>
                    <ShortOrderRows list={rows(own, 'openSalesOrders')} data={data} />
                    <CareRows list={rows(own, 'replacements').filter(isOpenCase)} data={data} />
                    <h2>Lịch hẹn điểm bán</h2>
                    <AppointmentRows list={rows(own, 'appointments')} data={data} />
                  </>
                )}
              </>
            )}
            {view === 'stores' && (
              <>
                <LoadState {...storeProfile} retry={storeProfile.reload} />
                {tab === 'details' ? (
                  <>
                    <h2>Thông tin điểm bán</h2>
                    <DetailGrid
                      items={[
                        ['Tên điểm bán', entity.name],
                        ['Người liên hệ', entity.owner],
                        ['Điện thoại', entity.phone],
                        ['Địa chỉ', entity.address],
                        ['Nhà phân phối', nppName(data, entity.nppId)],
                        ['Sales phụ trách', personName(data, entity.repId)],
                        [
                          'Tuyến bán hàng',
                          routes
                            .filter(
                              (r) =>
                                r.id === entity.routeId ||
                                records(r.stops).some((s) => s.storeId === entity.id),
                            )
                            .map((r) => textValue(r.name))
                            .join(', '),
                        ],
                        ['Nhóm khách hàng', entity.groupName],
                        ['Trạng thái', <Badge status={textValue(entity.status)} />],
                      ]}
                    />
                    <h2>Việc đang chờ xử lý</h2>
                    <ShortOrderRows
                      list={rows(data, 'openSalesOrders').filter((o) => o.storeId === entity.id)}
                      data={data}
                    />
                    <CareRows
                      list={rows(data, 'replacements').filter(
                        (r) => r.storeId === entity.id && isOpenCase(r),
                      )}
                      data={data}
                    />
                    <AppointmentRows
                      list={rows(data, 'appointments').filter(
                        (a) => a.storeId === entity.id && a.status === 'OPEN',
                      )}
                      data={data}
                    />
                  </>
                ) : (
                  <>
                    <LoadState {...section} retry={section.reload} />
                    <Table
                      rows={records(section.data.content)}
                      columns={
                        tab === 'orders'
                          ? [
                              {
                                key: 'code',
                                label: 'Đơn hàng',
                                render: (r) => (
                                  <Link to={`/orders?channel=sales&order=${r.id}`}>
                                    {textValue(r.code)}
                                  </Link>
                                ),
                              },
                              {
                                key: 'status',
                                label: 'Trạng thái',
                                render: (r) => <Badge status={textValue(r.status)} />,
                              },
                              {
                                key: 'total',
                                label: 'Giá trị đơn',
                                render: (r) => money(numberValue(r.total)),
                              },
                            ]
                          : tab === 'interactions'
                            ? [
                                {
                                  key: 'kind',
                                  label: 'Loại tương tác',
                                  render: (r) =>
                                    ({
                                      CALL: 'Cuộc gọi',
                                      VISIT: 'Ghé thăm',
                                      NOTE: 'Ghi chú',
                                      EMAIL: 'Email',
                                    })[textValue(r.kind)] || 'Trao đổi',
                                },
                                {
                                  key: 'note',
                                  label: 'Nội dung',
                                  render: (r) => textValue(r.note),
                                },
                                { key: 'at', label: 'Thời điểm', render: (r) => dateText(r.at) },
                              ]
                            : [
                                {
                                  key: 'title',
                                  label: tab === 'display' ? 'Ảnh trưng bày' : 'Nội dung',
                                  render: (r) => textValue(r.title) || `#${r.id}`,
                                },
                                {
                                  key: 'status',
                                  label: 'Trạng thái',
                                  render: (r) => <Badge status={textValue(r.status)} />,
                                },
                                {
                                  key: 'at',
                                  label: tab === 'display' ? 'Điểm duyệt' : 'Thời hạn',
                                  render: (r) =>
                                    tab === 'display' ? textValue(r.finalScore) : dateText(r.dueAt),
                                },
                              ]
                      }
                    />
                    {(numberValue(params.get('page')) > 0 || section.data.hasNext === true) && (
                      <div className="nsx-pager">
                        <button
                          className="btn"
                          disabled={!numberValue(params.get('page'))}
                          onClick={() => {
                            params.set('page', String(numberValue(params.get('page')) - 1));
                            setParams(params);
                          }}
                        >
                          Trước
                        </button>
                        <button
                          className="btn"
                          disabled={section.data.hasNext !== true}
                          onClick={() => {
                            params.set('page', String(numberValue(params.get('page')) + 1));
                            setParams(params);
                          }}
                        >
                          Sau
                        </button>
                      </div>
                    )}
                  </>
                )}
              </>
            )}
            {view === 'routes' && (
              <>
                {tab === 'details' ? (
                  <>
                    <h2>Thông tin tuyến bán hàng</h2>
                    <DetailGrid
                      items={[
                        ['Tên tuyến', entity.name],
                        ['NPP phục vụ', nppName(data, entity.nppId)],
                        ['Sales phụ trách', personName(data, entity.repId)],
                        ['Số điểm bán', schedule.length],
                      ]}
                    />
                  </>
                ) : (
                  <>
                    <h2>Danh sách khách hàng (điểm bán)</h2>
                    <div className="nsx-mcp-scroll">
                      <table className="nsx-mcp">
                        <thead>
                          <tr>
                            <th rowSpan={2}>Thứ tự</th>
                            <th rowSpan={2}>Điểm bán</th>
                            <th rowSpan={2}>Địa chỉ</th>
                            <th rowSpan={2}>Nhà phân phối</th>
                            <th colSpan={7}>Ngày viếng thăm</th>
                          </tr>
                          <tr>
                            {[2, 3, 4, 5, 6, 7, 'CN'].map((d) => (
                              <th key={d}>{d === 'CN' ? 'CN' : `Thứ ${d}`}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {schedule.map((s) => {
                            const outlet = stores.find((o) => o.id === s.storeId) || {};
                            return (
                              <tr key={textValue(s.storeId)}>
                                <td>{textValue(s.sequence)}</td>
                                <td>
                                  <Link
                                    to={`/network?view=stores&id=${s.storeId}`}
                                    onClick={() => setTab('details')}
                                  >
                                    {textValue(outlet.name)}
                                  </Link>
                                </td>
                                <td>{textValue(outlet.address)}</td>
                                <td>{nppName(data, entity.nppId)}</td>
                                {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                                  <td key={d}>
                                    <span
                                      aria-label={
                                        numberValue(s.weekday) === d
                                          ? 'Có lịch ghé'
                                          : 'Không có lịch'
                                      }
                                      className={`nsx-mcp-check ${numberValue(s.weekday) === d ? 'selected' : ''}`}
                                    >
                                      {numberValue(s.weekday) === d && <Check size={13} />}
                                    </span>
                                  </td>
                                ))}
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                      {!schedule.length && (
                        <div className="empty">Chưa có điểm bán trong tuyến.</div>
                      )}
                    </div>
                  </>
                )}
              </>
            )}
          </section>
          <DataStamp data={data} />
        </>
      )}
      {action && (
        <ActionForm
          title={
            {
              route: 'Thêm tuyến bán hàng',
              plan: 'Lập lịch ghé',
              handover: 'Bàn giao Sales',
              organization: picked.id ? 'Chỉnh sửa tổ chức' : 'Thêm tổ chức bán hàng',
              npp: 'Thêm nhà phân phối',
              store: 'Thêm điểm bán',
            }[action] || 'Cập nhật'
          }
          fields={
            action === 'npp'
              ? [
                  { key: 'code', label: 'Mã nhà phân phối' },
                  { key: 'name', label: 'Tên nhà phân phối' },
                  {
                    key: 'region',
                    label: 'Vùng bán hàng',
                    options: ['SOUTH', 'NORTH', 'CENTRAL'].map((value) => ({
                      value,
                      label: regionName(value),
                    })),
                  },
                  {
                    key: 'creditLimit',
                    label: 'Hạn mức công nợ (đ)',
                    type: 'number',
                    min: 0,
                    value: 0,
                  },
                ]
              : action === 'store'
                ? [
                    { key: 'name', label: 'Tên điểm bán' },
                    { key: 'owner', label: 'Người liên hệ', required: false },
                    { key: 'phone', label: 'Điện thoại', type: 'tel' },
                    { key: 'address', label: 'Địa chỉ' },
                    {
                      key: 'repId',
                      label: 'Sales phụ trách',
                      options: people.map((p) => ({
                        value: textValue(p.id),
                        label: textValue(p.name),
                      })),
                    },
                  ]
                : fields
          }
          intro={
            ['route', 'handover', 'store'].includes(action) ? (
              <label>
                Nhà phân phối
                <select value={nppId} onChange={(e) => setNpp(e.target.value)}>
                  {npps.map((n) => (
                    <option key={textValue(n.id)} value={textValue(n.id)}>
                      {textValue(n.name)}
                    </option>
                  ))}
                </select>
              </label>
            ) : undefined
          }
          onClose={() => setAction('')}
          onSubmit={submit}
        />
      )}
    </div>
  );
}
