import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Phone, MapPin, UserRound } from 'lucide-react';
import { useStore } from '../../shared/store';
import {
  command,
  records,
  record,
  textValue,
  numberValue,
  type RecordData,
} from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { Badge, Panel, PageHead, SearchBox, Table } from '../../shared/ui';
import StoreSections from './StoreSections';
import StoreCreate from './StoreCreate';
export default function CRM() {
  const { db, user, has, notify, refresh } = useStore();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const viewingStore = params.get('store');
  const [tab, setTab] = useState(
      ['leads', 'appointments'].includes(params.get('view') || '') ? params.get('view')! : 'stores',
    ),
    [query, setQuery] = useState(''),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const [npp, setNpp] = useState(user?.nppId || 0);
  const nppId = npp || Number(db.partners[0]?.id) || 0;
  const stores = useApi<RecordData[]>('/stores', []),
    leads = useApi<RecordData[]>('/leads', []),
    appointments = useApi<RecordData[]>('/appointments', []),
    staff = useApi<RecordData[]>(nppId ? `/staff?nppId=${nppId}` : null, []),
    profile = useApi<RecordData>(
      params.get('store') ? `/stores/${params.get('store')}/360` : null,
      {},
    );
  const storeOptions = stores.data.map((s) => ({
      value: textValue(s.id),
      label: textValue(s.name),
    })),
    staffOptions = staff.data
      .filter((s) => ['SALES', 'SUPERVISOR'].includes(textValue(s.role)))
      .map((s) => ({ value: textValue(s.id), label: textValue(s.name) }));
  let fields: FormField[] = [{ key: 'reason', label: 'Ghi chú / lý do', type: 'textarea' }];
  if (action === 'store' || action === 'lead')
    fields = [
      { key: 'name', label: 'Tên cửa hàng / khách hàng' },
      { key: 'owner', label: 'Người liên hệ', required: false },
      { key: 'phone', label: 'Điện thoại', type: 'tel' },
      { key: 'address', label: 'Địa chỉ' },
      {
        key: 'repId',
        label: 'Nhân viên phụ trách',
        options: staffOptions,
        value: user?.role === 'SALES' ? user.id : undefined,
      },
      {
        key: 'latitude',
        label: 'Vĩ độ',
        type: 'number',
        step: 'any',
        required: false,
        advanced: true,
      },
      {
        key: 'longitude',
        label: 'Kinh độ',
        type: 'number',
        step: 'any',
        required: false,
        advanced: true,
      },
    ];
  if (action === 'appointment')
    fields = [
      {
        key: 'storeId',
        label: 'Cửa hàng',
        options: storeOptions,
        value: params.get('store') || undefined,
      },
      { key: 'title', label: 'Nội dung hẹn' },
      { key: 'dueAt', label: 'Thời gian hẹn', type: 'datetime-local' },
    ];
  if (action === 'interaction')
    fields = [
      {
        key: 'storeId',
        label: 'Cửa hàng',
        options: storeOptions,
        value: params.get('store') || undefined,
      },
      {
        key: 'kind',
        label: 'Loại tương tác',
        options: Object.entries({
          CALL: 'Cuộc gọi',
          VISIT: 'Ghé thăm',
          EMAIL: 'Email',
          NOTE: 'Ghi chú',
        }).map(([value, label]) => ({ value, label })),
      },
      { key: 'note', label: 'Nội dung', type: 'textarea' },
    ];
  if (action === 'lead-transition')
    fields = [
      {
        key: 'status',
        label: 'Bước tiếp theo',
        options: (
          {
            NOT_CONTACTED: ['CONTACTED', 'LOST'],
            CONTACTED: ['CONSULTING', 'LOST'],
            CONSULTING: ['WON', 'LOST'],
            LOST: ['CONSULTING'],
          }[textValue(selected.status)] || []
        ).map((value) => ({ value, label: value })),
      },
      ...fields,
    ];
  async function submit(values: Record<string, string>) {
    if (action === 'store' || action === 'lead')
      await command(action === 'store' ? '/stores' : '/leads', {
        ...values,
        nppId,
        repId: Number(values.repId),
        latitude: values.latitude ? Number(values.latitude) : null,
        longitude: values.longitude ? Number(values.longitude) : null,
      });
    if (action === 'appointment')
      await command('/appointments', {
        ...values,
        storeId: Number(values.storeId),
        dueAt: new Date(values.dueAt).toISOString(),
      });
    if (action === 'interaction')
      await command('/interactions', { ...values, storeId: Number(values.storeId) });
    if (action === 'complete') await command(`/appointments/${selected.id}/complete`, values);
    if (action === 'lead-transition') await command(`/leads/${selected.id}/transition`, values);
    stores.reload();
    leads.reload();
    appointments.reload();
    profile.reload();
    await refresh();
    notify('Đã lưu dữ liệu CRM.');
  }
  const open = (value: string, row: RecordData = {}) => {
    setSelected(row);
    setAction(value);
  };
  const state = tab === 'stores' ? stores : tab === 'leads' ? leads : appointments;
  const customer = record(profile.data.store);
  const customerName =
    textValue(customer.name) ||
    textValue(stores.data.find((s) => String(s.id) === viewingStore)?.name);
  const assigned = staff.data.find((s) => s.id === customer.repId);
  const actionStore = stores.data.find((s) => s.id === selected.storeId);
  return (
    <>
      <PageHead title={viewingStore ? customerName || 'Hồ sơ điểm bán' : 'Điểm bán & CRM'}>
        {viewingStore && (
          <button className="text-button" onClick={() => setParams({})}>
            <ArrowLeft size={16} />
            Điểm bán
          </button>
        )}
        {viewingStore && has('SALES_CREATE') && (
          <button
            className="btn primary"
            onClick={() => navigate('/catalog?store=' + viewingStore)}
          >
            Lập đơn
          </button>
        )}
        {has('CRM_WRITE') && (
          <>
            <button
              className={viewingStore ? 'btn' : 'btn primary'}
              onClick={() =>
                open(
                  viewingStore
                    ? 'appointment'
                    : tab === 'leads'
                      ? 'lead'
                      : tab === 'appointments'
                        ? 'appointment'
                        : 'store',
                )
              }
            >
              {viewingStore
                ? 'Tạo lịch hẹn'
                : tab === 'leads'
                  ? 'Tạo cơ hội'
                  : tab === 'appointments'
                    ? 'Tạo lịch hẹn'
                    : 'Thêm cửa hàng'}
            </button>
            <button className="btn" onClick={() => open('interaction')}>
              Ghi tương tác
            </button>
          </>
        )}
      </PageHead>
      {!viewingStore && (
        <>
          {!user?.nppId && (
            <label>
              Nhà phân phối
              <select value={nppId} onChange={(e) => setNpp(Number(e.target.value))}>
                {db.partners.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div className="tabs">
            {[
              ['stores', 'Cửa hàng'],
              ['leads', 'Cơ hội'],
              ['appointments', 'Lịch hẹn'],
            ].map(([value, label]) => (
              <button
                key={value}
                className={tab === value ? 'active' : ''}
                onClick={() => setTab(value)}
              >
                {label}
              </button>
            ))}
          </div>
          <LoadState {...state} retry={state.reload} />
          <Panel>
            <div className="table-toolbar">
              <SearchBox value={query} onChange={setQuery} />
            </div>
            <Table
              rows={state.data.filter((r) =>
                JSON.stringify(r).toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')),
              )}
              searchText={query}
              onRow={tab === 'stores' ? (r) => setParams({ store: textValue(r.id) }) : undefined}
              columns={
                tab === 'appointments'
                  ? [
                      { key: 'title', label: 'Nội dung', render: (r) => textValue(r.title) },
                      {
                        key: 'store',
                        label: 'Cửa hàng',
                        render: (r) =>
                          (stores.data.find((s) => s.id === r.storeId)?.name as string) ||
                          textValue(r.storeId),
                      },
                      {
                        key: 'dueAt',
                        label: 'Thời gian',
                        render: (r) => new Date(textValue(r.dueAt)).toLocaleString('vi-VN'),
                      },
                      {
                        key: 'status',
                        label: 'Trạng thái',
                        render: (r) => <Badge status={textValue(r.status)} />,
                      },
                      {
                        key: 'action',
                        label: 'Xử lý',
                        render: (r) =>
                          r.status === 'OPEN' && has('CRM_WRITE') ? (
                            <button className="btn" onClick={() => open('complete', r)}>
                              Hoàn thành
                            </button>
                          ) : null,
                      },
                    ]
                  : [
                      {
                        key: 'name',
                        label: 'Tên',
                        render: (r) => <strong>{textValue(r.name)}</strong>,
                      },
                      { key: 'phone', label: 'Điện thoại', render: (r) => textValue(r.phone) },
                      { key: 'address', label: 'Địa chỉ', render: (r) => textValue(r.address) },
                      {
                        key: 'status',
                        label: 'Trạng thái',
                        render: (r) => <Badge status={textValue(r.status)} />,
                      },
                      {
                        key: 'action',
                        label: 'Xử lý',
                        render: (r) =>
                          tab === 'leads' && r.status !== 'WON' && has('CRM_WRITE') ? (
                            <button className="btn" onClick={() => open('lead-transition', r)}>
                              Cập nhật cơ hội
                            </button>
                          ) : tab === 'stores' && has('SALES_CREATE') ? (
                            <button
                              className="btn"
                              onClick={(e) => {
                                e.stopPropagation();
                                navigate('/catalog?store=' + r.id);
                              }}
                            >
                              Tạo đơn
                            </button>
                          ) : null,
                      },
                    ]
              }
            />
          </Panel>
        </>
      )}
      {viewingStore && (
        <div className="crm-profile">
          <LoadState {...profile} retry={profile.reload} />
          {Boolean(customer.id) && (
            <>
              <div className="crm-identity">
                <div className="crm-monogram">
                  {customerName
                    .split(' ')
                    .filter(Boolean)
                    .slice(-2)
                    .map((w) => w[0])
                    .join('')}
                </div>
                <dl>
                  <div>
                    <dt>
                      <UserRound size={16} />
                      Người liên hệ
                    </dt>
                    <dd>{textValue(customer.owner) || '—'}</dd>
                  </div>
                  <div>
                    <dt>
                      <Phone size={16} />
                      Điện thoại
                    </dt>
                    <dd>
                      <a href={'tel:' + textValue(customer.phone)}>
                        {textValue(customer.phone) || '—'}
                      </a>
                    </dd>
                  </div>
                  <div className="crm-address">
                    <dt>
                      <MapPin size={16} />
                      Địa chỉ
                    </dt>
                    <dd>{textValue(customer.address) || '—'}</dd>
                  </div>
                  <div>
                    <dt>Sales phụ trách</dt>
                    <dd>{textValue(assigned?.name) || `#${customer.repId}`}</dd>
                  </div>
                  <div>
                    <dt>Trạng thái</dt>
                    <dd>
                      <Badge status={textValue(customer.status)} />
                    </dd>
                  </div>
                  <div>
                    <dt>Nhóm điểm bán</dt>
                    <dd>{textValue(customer.groupName) || 'Chưa phân nhóm'}</dd>
                  </div>
                  <div>
                    <dt>Tuyến bán hàng</dt>
                    <dd>{customer.routeId ? `Tuyến #${customer.routeId}` : 'Chưa phân tuyến'}</dd>
                  </div>
                </dl>
              </div>
              <StoreSections
                storeId={viewingStore}
                key={viewingStore}
                onComplete={has('CRM_WRITE') ? (row) => open('complete', row) : undefined}
              />
            </>
          )}
        </div>
      )}
      {action === 'store' ? (
        <StoreCreate staff={staffOptions} onClose={() => setAction('')} onSubmit={submit} />
      ) : (
        action && (
          <ActionForm
            title={
              {
                store: 'Thêm cửa hàng',
                lead: 'Tạo cơ hội',
                appointment: 'Tạo lịch hẹn',
                interaction: 'Ghi tương tác',
                complete: 'Kết quả hẹn',
                'lead-transition': 'Cập nhật cơ hội',
              }[action] || action
            }
            fields={fields}
            context={viewingStore ? customerName : textValue(actionStore?.name) || undefined}
            onClose={() => setAction('')}
            onSubmit={submit}
          />
        )
      )}
    </>
  );
}
