import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useStore } from '../../shared/store';
import { command, textValue, numberValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { Badge, PageHead, Panel, Table, SearchBox } from '../../shared/ui';
import { money, today } from '../../shared/utils';
import { productImage } from '../../shared/productMedia';
import Documents from '../policies/Documents';
import TicketCase from '../tickets/Case';
const titles: Record<string, string> = {
  partners: 'Mạng lưới nhà phân phối',
  inventory: 'Kho & tồn hàng',
  routes: 'Tuyến & đội ngũ',
  policies: 'Chính sách & ưu đãi',
  tickets: 'Trung tâm hỗ trợ',
  settings: 'Quản trị hệ thống',
};
export default function Management({ page }: { page: string }) {
  const { db, user, role, has, products, notify, refresh } = useStore();
  const [params] = useSearchParams();
  const [ticketId, setTicketId] = useState('');
  const [query, setQuery] = useState(params.get('q') || ''),
    [tab, setTab] = useState(page === 'inventory' ? 'stock' : 'users'),
    [warehouse, setWarehouse] = useState(''),
    [npp, setNpp] = useState(user?.nppId || 0),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const nppId = npp || Number(db.partners[0]?.id) || 0;
  const warehouses = useApi<RecordData[]>(page === 'inventory' ? '/warehouses' : null, []);
  const warehouseId = warehouse || textValue(warehouses.data[0]?.id);
  const staff = useApi<RecordData[]>(
    page === 'routes' && nppId ? `/staff?nppId=${nppId}` : null,
    [],
  );
  const path =
    page === 'partners'
      ? '/organizations'
      : page === 'inventory'
        ? tab === 'products'
          ? '/catalog/skus'
          : tab === 'prices'
            ? '/pricing/prices'
            : warehouseId
              ? `/inventory/${tab === 'stocktakes' ? 'stocktakes' : 'balances'}?warehouseId=${warehouseId}`
              : null
        : page === 'routes'
          ? '/routes'
          : page === 'policies'
            ? '/policies'
            : page === 'tickets'
              ? '/tickets'
              : has('USER_MANAGE')
                ? `/admin/${tab}`
                : null;
  const state = useApi<RecordData[]>(path, []);
  const organizations = useApi<RecordData[]>(
    page === 'partners' ? '/organizations/tree' : null,
    [],
  );
  const roleDefinitions = useApi<RecordData[]>(page === 'settings' ? '/admin/roles' : null, []);
  const grants = useApi<RecordData[]>(
    action === 'workspace' && selected.id ? `/admin/users/${selected.id}/workspaces` : null,
    [],
  );
  const nppGroups = useApi<RecordData[]>(
    page === 'inventory' && has('POLICY_WRITE') ? '/catalog/references?kind=NPP_GROUP' : null,
    [],
  );
  const storeGroups = useApi<RecordData[]>(
    page === 'inventory' && has('POLICY_WRITE') ? '/catalog/references?kind=STORE_GROUP' : null,
    [],
  );
  const open = (name: string, row: RecordData = {}) => {
    setSelected(row);
    setAction(name);
  };
  const partnerOptions = [
    { value: 0, label: 'Toàn hệ thống / không thuộc NPP' },
    ...db.partners.map((p) => ({ value: p.id, label: p.name })),
  ];
  const staffOptions = staff.data.map((s) => ({
    value: textValue(s.id),
    label: textValue(s.name),
  }));
  const reason: FormField = { key: 'reason', label: 'Lý do / ghi chú', type: 'textarea' };
  let fields: FormField[] = [reason];
  if (action === 'organization')
    fields = [
      { key: 'name', label: 'Tên đơn vị', value: textValue(selected.name) },
      { key: 'region', label: 'Vùng', value: textValue(selected.region) },
      {
        key: 'parentId',
        label: 'Đơn vị cha',
        value: Number(selected.parentId) || 0,
        options: [
          { value: 0, label: 'Không có cha (chỉ NSX gốc)' },
          ...organizations.data
            .filter((o) => o.id !== selected.id && o.type !== 'NPP')
            .map((o) => ({ value: textValue(o.id), label: textValue(o.name) })),
        ],
      },
      reason,
    ];
  if (action === 'workflow')
    fields = [
      {
        key: 'type',
        label: 'Loại chứng từ',
        options: ['SELL_IN', 'TRADE_REQUEST', 'POLICY', 'ADJUSTMENT'].map((value) => ({
          value,
          label: value,
        })),
      },
      { key: 'role1', label: 'Quyền / vai trò duyệt cấp 1', value: 'ORDER_APPROVE' },
      { key: 'sla1', label: 'SLA cấp 1 (giờ)', type: 'number', min: 1, value: 24 },
      { key: 'role2', label: 'Vai trò cấp 2 (trống để chỉ có một cấp)', required: false },
      {
        key: 'threshold2',
        label: 'Ngưỡng giá trị cần cấp 2',
        type: 'number',
        min: 0,
        required: false,
      },
      { key: 'sla2', label: 'SLA cấp 2 (giờ)', type: 'number', value: 24, min: 1, required: false },
      reason,
    ];
  if (action === 'parameter')
    fields = [
      {
        key: 'value',
        label: textValue(selected.key),
        type: 'number',
        value: numberValue(selected.value),
        min: numberValue(selected.min),
        step: selected.key === 'credit-warn-ratio' ? '0.01' : '1',
      },
      reason,
    ];
  if (action === 'role')
    fields = [
      { key: 'code', label: 'Mã vai trò (in hoa, dấu gạch dưới)', value: textValue(selected.role) },
      { key: 'name', label: 'Tên vai trò', value: textValue(selected.name) },
      {
        key: 'baseRole',
        label: 'Loại vai trò gốc',
        value: textValue(selected.baseRole) || 'SALES',
        options: roleDefinitions.data
          .filter((r) => r.builtIn)
          .map((r) => ({ value: textValue(r.role), label: textValue(r.name) })),
      },
      {
        key: 'authorities',
        label: 'Quyền (trong giới hạn vai trò gốc)',
        type: 'multiselect',
        required: false,
        selected: Array.isArray(selected.authorities) ? selected.authorities.map(String) : [],
        options: [
          ...new Set(
            roleDefinitions.data.flatMap((r) =>
              Array.isArray(r.authorities) ? r.authorities.map(String) : [],
            ),
          ),
        ]
          .sort()
          .map((value) => ({ value, label: value })),
      },
      {
        key: 'active',
        label: 'Sử dụng',
        value: selected.active === false ? 'false' : 'true',
        options: [
          { value: 'true', label: 'Hoạt động' },
          { value: 'false', label: 'Ngừng sử dụng' },
        ],
      },
      reason,
    ];
  if (action === 'workspace')
    fields = [
      {
        key: 'roleCode',
        label: 'Vai trò',
        options: roleDefinitions.data
          .filter((r) => r.active)
          .map((r) => ({ value: textValue(r.role), label: textValue(r.name) })),
      },
      {
        key: 'scopeType',
        label: 'Loại phạm vi',
        options: ['NPP', 'REGION', 'ALL'].map((value) => ({ value, label: value })),
      },
      { key: 'scopeId', label: 'Mã NPP / vùng / ALL' },
      {
        key: 'remove',
        label: 'Thao tác',
        options: [
          { value: 'false', label: 'Cấp thêm phạm vi' },
          { value: 'true', label: 'Thu hồi phạm vi' },
        ],
      },
      reason,
    ];
  if (action === 'transfer')
    fields = [
      {
        key: 'destinationWarehouseId',
        label: 'Kho nhận',
        options: warehouses.data
          .filter((w) => String(w.id) !== warehouseId && w.type !== 'QUARANTINE')
          .map((w) => ({ value: textValue(w.id), label: textValue(w.name) })),
      },
      {
        key: 'skuId',
        label: 'Sản phẩm',
        options: products.map((p) => ({ value: p.id, label: p.name })),
      },
      { key: 'qtyBase', label: 'Số lượng đơn vị cơ sở', type: 'number', min: 0.001, step: '0.001' },
      reason,
    ];
  if (action === 'product')
    fields = [
      { key: 'code', label: 'Mã sản phẩm' },
      { key: 'name', label: 'Tên sản phẩm' },
      { key: 'category', label: 'Danh mục' },
      { key: 'baseUnit', label: 'Đơn vị cơ sở', value: 'CHAI' },
      {
        key: 'minOrder',
        label: 'Số lượng cơ sở tối thiểu',
        type: 'number',
        value: 1,
        min: 0.001,
        step: '0.001',
      },
      { key: 'packCode', label: 'Đơn vị lốc / gói', value: 'LOC', required: false },
      {
        key: 'packFactor',
        label: 'Số đơn vị cơ sở trong lốc',
        type: 'number',
        value: 6,
        min: 0.001,
        step: '0.001',
        required: false,
      },
      { key: 'caseCode', label: 'Đơn vị thùng / kiện', value: 'THUNG', required: false },
      {
        key: 'caseFactor',
        label: 'Số đơn vị cơ sở trong thùng',
        type: 'number',
        value: 24,
        min: 0.001,
        step: '0.001',
        required: false,
      },
    ];
  if (action === 'price')
    fields = [
      { key: 'nppId', label: 'Phạm vi áp dụng', options: partnerOptions },
      {
        key: 'nppGroupId',
        label: 'Nhóm NPP (nếu áp dụng)',
        value: 0,
        options: [
          { value: 0, label: 'Không giới hạn nhóm' },
          ...nppGroups.data
            .filter((g) => g.active)
            .map((g) => ({ value: textValue(g.id), label: textValue(g.name) })),
        ],
      },
      {
        key: 'storeGroupId',
        label: 'Nhóm cửa hàng (nếu áp dụng)',
        value: 0,
        options: [
          { value: 0, label: 'Không giới hạn nhóm' },
          ...storeGroups.data
            .filter((g) => g.active)
            .map((g) => ({ value: textValue(g.id), label: textValue(g.name) })),
        ],
      },
      {
        key: 'skuId',
        label: 'Sản phẩm',
        options: products.map((p) => ({ value: p.id, label: p.name })),
      },
      { key: 'uom', label: 'Đơn vị bán', value: 'THUNG' },
      { key: 'price', label: 'Giá bán', type: 'number', min: 0, step: '0.01' },
      { key: 'validFrom', label: 'Từ ngày', type: 'date', value: today() },
      { key: 'validTo', label: 'Đến ngày', type: 'date', value: today() },
      reason,
    ];
  if (action === 'partner')
    fields = [
      { key: 'code', label: 'Mã NPP' },
      { key: 'name', label: 'Tên nhà phân phối' },
      { key: 'region', label: 'Vùng' },
      { key: 'creditLimit', label: 'Hạn mức ban đầu', type: 'number', min: 0, step: '0.01' },
    ];
  if (action === 'receipt')
    fields = [
      {
        key: 'skuId',
        label: 'Sản phẩm',
        options: products.map((p) => ({ value: p.id, label: p.name })),
      },
      { key: 'lotNo', label: 'Số lô' },
      { key: 'expiryDate', label: 'Hạn dùng thực của lô', type: 'date' },
      { key: 'qtyBase', label: 'Số lượng đơn vị cơ sở', type: 'number', min: 0.001, step: '0.001' },
      reason,
    ];
  if (action === 'stocktake')
    fields = [
      {
        key: 'counted',
        label: 'Số đếm đơn vị cơ sở',
        type: 'number',
        value: numberValue(selected.onHand),
        min: 0,
        step: '0.001',
      },
      reason,
    ];
  if (action === 'staff' || action === 'user')
    fields = [
      { key: 'username', label: 'Email đăng nhập', type: 'email' },
      { key: 'name', label: 'Họ và tên' },
      { key: 'password', label: 'Mật khẩu khởi tạo (tối thiểu 12 ký tự)', type: 'password' },
      {
        key: 'role',
        label: 'Vai trò',
        options: (action === 'staff'
          ? ['SALES', 'SUPERVISOR', 'WAREHOUSE_NPP', 'ACCOUNTANT_NPP', 'DRIVER']
          : roleDefinitions.data.filter((r) => r.active).map((r) => textValue(r.role))
        ).map((value) => ({ value, label: value })),
      },
      ...(action === 'user'
        ? [
            { key: 'nppId', label: 'Phạm vi NPP', options: partnerOptions },
            { key: 'region', label: 'Vùng (để trống nếu toàn quốc)', required: false },
          ]
        : []),
    ];
  if (action === 'access')
    fields = [
      {
        key: 'role',
        label: 'Vai trò',
        value: textValue(selected.role),
        options: roleDefinitions.data
          .filter((r) => r.active)
          .map((r) => textValue(r.role))
          .map((value) => ({ value, label: value })),
      },
      {
        key: 'nppId',
        label: 'Phạm vi NPP',
        options: partnerOptions,
        value: numberValue(selected.nppId),
      },
      { key: 'region', label: 'Vùng', required: false },
      {
        key: 'enabled',
        label: 'Truy cập',
        options: [
          { value: 'true', label: 'Cho phép' },
          { value: 'false', label: 'Khóa tài khoản' },
        ],
        value: selected.enabled ? 'true' : 'false',
      },
      reason,
    ];
  if (action === 'route')
    fields = [
      { key: 'name', label: 'Tên tuyến' },
      { key: 'repId', label: 'Nhân viên phụ trách', options: staffOptions },
      {
        key: 'storeIds',
        label: 'Cửa hàng trong tuyến',
        type: 'multiselect',
        options: db.stores.map((s) => ({ value: s.id, label: s.name })),
      },
      {
        key: 'weekday',
        label: 'Ngày ghé',
        options: ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'].map(
          (label, index) => ({ value: index + 1, label }),
        ),
      },
    ];
  if (action === 'plan')
    fields = [{ key: 'day', label: 'Ngày sinh lượt ghé', type: 'date', value: today() }];
  if (action === 'handover')
    fields = [
      { key: 'fromRepId', label: 'Người bàn giao', options: staffOptions },
      { key: 'toRepId', label: 'Người nhận', options: staffOptions },
      reason,
    ];
  if (action === 'policy')
    fields = [
      { key: 'name', label: 'Tên chương trình' },
      { key: 'nppId', label: 'Áp dụng', options: partnerOptions },
      { key: 'percent', label: 'Ưu đãi (%)', type: 'number', min: 0, step: '0.01' },
      { key: 'budget', label: 'Ngân sách', type: 'number', min: 0, step: '0.01' },
      {
        key: 'minQty',
        label: 'Số lượng cơ sở tối thiểu',
        type: 'number',
        value: 0,
        min: 0,
        step: '0.001',
      },
      {
        key: 'minAmount',
        label: 'Giá trị tối thiểu',
        type: 'number',
        value: 0,
        min: 0,
        step: '0.01',
      },
      { key: 'priority', label: 'Độ ưu tiên', type: 'number', value: 0 },
      { key: 'exclusionGroup', label: 'Nhóm loại trừ', required: false },
      { key: 'startDate', label: 'Hiệu lực từ', type: 'date', value: today() },
      { key: 'endDate', label: 'Đến', type: 'date', value: today() },
    ];
  if (action === 'ticket')
    fields = [
      {
        key: 'nppId',
        label: 'Nhà phân phối',
        options: db.partners.map((p) => ({ value: p.id, label: p.name })),
        value: nppId,
      },
      {
        key: 'storeId',
        label: 'Cửa hàng (nếu có)',
        options: [
          { value: 0, label: 'Không gắn cửa hàng' },
          ...db.stores.map((s) => ({ value: s.id, label: s.name })),
        ],
      },
      { key: 'title', label: 'Tiêu đề' },
      {
        key: 'category',
        label: 'Phân loại',
        options: ['DELIVERY_SHORT', 'QUALITY', 'CREDIT', 'POLICY', 'OTHER'].map((value) => ({
          value,
          label: value,
        })),
      },
      { key: 'description', label: 'Nội dung', type: 'textarea' },
    ];
  if (action === 'ticket-state')
    fields = [
      {
        key: 'status',
        label: 'Bước tiếp theo',
        options: (
          {
            NEW: ['ACKNOWLEDGED', 'REJECTED'],
            ACKNOWLEDGED: ['IN_PROGRESS'],
            IN_PROGRESS: ['WAITING_INFO', 'RESOLVED'],
            WAITING_INFO: ['IN_PROGRESS'],
            RESOLVED: ['CLOSED', 'REOPENED'],
            CLOSED: ['REOPENED'],
            REOPENED: ['IN_PROGRESS'],
          }[textValue(selected.status)] || []
        ).map((value) => ({ value, label: value })),
      },
      reason,
    ];
  async function save(values: Record<string, string>) {
    if (action === 'organization')
      await command(`/organizations/${selected.id}`, {
        ...values,
        parentId: Number(values.parentId) || null,
        version: selected.version,
      });
    if (action === 'workflow')
      await command('/admin/workflows', {
        type: values.type,
        reason: values.reason,
        steps: [
          { step: 1, role: values.role1, minAmount: 0, slaHours: Number(values.sla1) },
          ...(values.role2
            ? [
                {
                  step: 2,
                  role: values.role2,
                  minAmount: Number(values.threshold2),
                  slaHours: Number(values.sla2),
                },
              ]
            : []),
        ],
      });
    if (action === 'parameter')
      await command(`/admin/parameters/${selected.key}`, {
        value: Number(values.value),
        reason: values.reason,
      });
    if (action === 'role')
      await command('/admin/roles', {
        ...values,
        authorities: values.authorities ? values.authorities.split(',') : [],
        active: values.active === 'true',
        version: selected.version ?? null,
      });
    if (action === 'workspace')
      await command(`/admin/users/${selected.id}/workspaces`, {
        ...values,
        remove: values.remove === 'true',
      });
    if (action === 'retry-outbox') await command(`/admin/outbox/${selected.id}/retry`, values);
    if (action === 'transfer')
      await command('/inventory/transfers', {
        ...values,
        sourceWarehouseId: Number(warehouseId),
        destinationWarehouseId: Number(values.destinationWarehouseId),
        skuId: Number(values.skuId),
        qtyBase: Number(values.qtyBase),
      });
    if (action === 'product') {
      const factors: Record<string, number> = { [values.baseUnit]: 1 };
      if (values.packCode) factors[values.packCode] = Number(values.packFactor);
      if (values.caseCode) factors[values.caseCode] = Number(values.caseFactor);
      await command('/catalog/skus', {
        code: values.code,
        name: values.name,
        category: values.category,
        baseUnit: values.baseUnit,
        minOrder: Number(values.minOrder),
        factors,
      });
    }
    if (action === 'price')
      await command('/pricing/prices', {
        ...values,
        nppId: Number(values.nppId) || null,
        skuId: Number(values.skuId),
        price: Number(values.price),
        nppGroupId: Number(values.nppGroupId) || null,
        storeGroupId: Number(values.storeGroupId) || null,
      });
    if (action === 'approve-stocktake')
      await command(`/inventory/stocktakes/${selected.id}/approve`, values);
    if (action === 'partner')
      await command('/organizations', { ...values, creditLimit: Number(values.creditLimit) });
    if (action === 'receipt')
      await command('/inventory/receipts', {
        ...values,
        warehouseId: Number(warehouseId),
        skuId: Number(values.skuId),
        qtyBase: Number(values.qtyBase),
      });
    if (action === 'stocktake') {
      const take = await command('/inventory/stocktakes', {
        warehouseId: Number(warehouseId),
        counted: { [textValue(selected.id)]: Number(values.counted) },
        reason: values.reason,
      });
      notify(`Đã tạo kiểm kê #${take.id}; cần người khác duyệt.`);
    }
    if (action === 'staff') await command('/staff', { ...values, nppId });
    if (action === 'user')
      await command('/admin/users', {
        ...values,
        nppId: Number(values.nppId) || null,
        region: values.region || null,
      });
    if (action === 'access')
      await command(`/admin/users/${selected.id}/access`, {
        ...values,
        nppId: Number(values.nppId) || null,
        region: values.region || null,
        enabled: values.enabled === 'true',
      });
    if (action === 'route')
      await command('/routes', {
        ...values,
        nppId,
        repId: Number(values.repId),
        storeIds: values.storeIds.split(',').map(Number),
        weekday: Number(values.weekday),
      });
    if (action === 'plan') await command(`/routes/${selected.id}/plan`, values);
    if (action === 'handover')
      await command('/handovers', {
        ...values,
        nppId,
        fromRepId: Number(values.fromRepId),
        toRepId: Number(values.toRepId),
      });
    if (action === 'policy')
      await command('/policies', {
        ...values,
        nppId: Number(values.nppId) || null,
        skuId: null,
        percent: Number(values.percent),
        budget: Number(values.budget),
        minQty: Number(values.minQty),
        minAmount: Number(values.minAmount),
        priority: Number(values.priority),
        exclusionGroup: values.exclusionGroup || null,
      });
    if (action === 'publish') await command(`/policies/${selected.id}/publish`, values);
    if (action === 'ticket')
      await command('/tickets', {
        ...values,
        nppId: Number(values.nppId),
        storeId: Number(values.storeId) || null,
      });
    if (action === 'ticket-state') await command(`/tickets/${selected.id}/transition`, values);
    state.reload();
    roleDefinitions.reload();
    organizations.reload();
    staff.reload();
    await refresh();
    notify('Đã lưu thay đổi trên máy chủ.');
  }
  const columns =
    page === 'partners'
      ? [
          { key: 'code', label: 'Mã' },
          { key: 'name', label: 'Nhà phân phối' },
          { key: 'region', label: 'Vùng' },
          {
            key: 'action',
            label: 'Cập nhật',
            render: (r: RecordData) =>
              has('USER_MANAGE') ? (
                <button
                  className="btn"
                  onClick={() =>
                    open('organization', organizations.data.find((o) => o.id === r.id) || r)
                  }
                >
                  Sửa tổ chức
                </button>
              ) : null,
          },
        ]
      : page === 'inventory'
        ? [
            {
              key: 'skuId',
              label: 'Sản phẩm',
              render: (r: RecordData) => {
                const name =
                  products.find((p) => p.id === textValue(r.skuId))?.name || textValue(r.skuId);
                return user?.nppId ? (
                  <div className="stock-product">
                    {productImage(name) && <img src={productImage(name)} alt="" loading="lazy" />}
                    <span>
                      {name}
                      <small>SKU {textValue(r.skuId)}</small>
                    </span>
                  </div>
                ) : (
                  name
                );
              },
            },
            { key: 'lotNo', label: 'Lô' },
            { key: 'expiryDate', label: 'Hạn dùng' },
            { key: 'onHand', label: 'Tồn cơ sở' },
            { key: 'reserved', label: 'Đang giữ' },
            { key: 'available', label: 'Khả dụng' },
            {
              key: 'action',
              label: 'Kiểm kê',
              render: (r: RecordData) =>
                has('WAREHOUSE_WRITE') ? (
                  <button className="btn" onClick={() => open('stocktake', r)}>
                    Ghi số đếm
                  </button>
                ) : null,
            },
          ]
        : page === 'routes'
          ? [
              { key: 'name', label: 'Tuyến' },
              { key: 'nppId', label: 'NPP' },
              { key: 'repId', label: 'Phụ trách' },
              {
                key: 'action',
                label: 'Thao tác',
                render: (r: RecordData) =>
                  has('ROUTE_WRITE') ? (
                    <button className="btn" onClick={() => open('plan', r)}>
                      Sinh lượt ghé
                    </button>
                  ) : null,
              },
            ]
          : page === 'policies'
            ? [
                { key: 'name', label: 'Chương trình' },
                { key: 'percentOff', label: 'Ưu đãi (%)' },
                {
                  key: 'budget',
                  label: 'Ngân sách',
                  render: (r: RecordData) => money(numberValue(r.budget)),
                },
                {
                  key: 'usedBudget',
                  label: 'Đã giữ',
                  render: (r: RecordData) => money(numberValue(r.usedBudget)),
                },
                {
                  key: 'status',
                  label: 'Trạng thái',
                  render: (r: RecordData) => <Badge status={textValue(r.status)} />,
                },
                {
                  key: 'action',
                  label: 'Phát hành',
                  render: (r: RecordData) =>
                    has('POLICY_WRITE') &&
                    ['DRAFT', 'PENDING_APPROVAL'].includes(textValue(r.status)) ? (
                      <button className="btn" onClick={() => open('publish', r)}>
                        Duyệt phát hành
                      </button>
                    ) : null,
                },
              ]
            : page === 'tickets'
              ? [
                  { key: 'title', label: 'Yêu cầu' },
                  { key: 'category', label: 'Loại' },
                  { key: 'ownerId', label: 'Phụ trách' },
                  { key: 'dueAt', label: 'Hạn xử lý' },
                  {
                    key: 'status',
                    label: 'Trạng thái',
                    render: (r: RecordData) => <Badge status={textValue(r.status)} />,
                  },
                  {
                    key: 'action',
                    label: 'Xử lý',
                    render: (r: RecordData) =>
                      has('TICKET_WRITE') ? (
                        <button className="btn" onClick={() => open('ticket-state', r)}>
                          Cập nhật
                        </button>
                      ) : null,
                  },
                ]
              : tab === 'users'
                ? [
                    { key: 'username', label: 'Email' },
                    { key: 'name', label: 'Họ tên' },
                    { key: 'role', label: 'Vai trò' },
                    { key: 'nppId', label: 'NPP' },
                    {
                      key: 'enabled',
                      label: 'Truy cập',
                      render: (r: RecordData) => (r.enabled ? 'Hoạt động' : 'Khóa'),
                    },
                    {
                      key: 'action',
                      label: 'Quyền',
                      render: (r: RecordData) =>
                        r.id !== user?.id ? (
                          <span className="toolbar">
                            <button className="btn" onClick={() => open('workspace', r)}>
                              Phạm vi
                            </button>
                            <button className="btn" onClick={() => open('access', r)}>
                              Đổi quyền / khóa
                            </button>
                          </span>
                        ) : null,
                    },
                  ]
                : tab === 'roles'
                  ? [
                      { key: 'role', label: 'Vai trò' },
                      { key: 'authorities', label: 'Quyền' },
                      {
                        key: 'action',
                        label: 'Cập nhật',
                        render: (r: RecordData) =>
                          !r.builtIn ? (
                            <button className="btn" onClick={() => open('role', r)}>
                              Sửa vai trò
                            </button>
                          ) : (
                            <span>Mặc định</span>
                          ),
                      },
                    ]
                  : tab === 'parameters'
                    ? [
                        { key: 'key', label: 'Tham số' },
                        { key: 'value', label: 'Giá trị' },
                        { key: 'min', label: 'Tối thiểu' },
                        { key: 'max', label: 'Tối đa' },
                        {
                          key: 'action',
                          label: 'Cập nhật',
                          render: (r: RecordData) => (
                            <button className="btn" onClick={() => open('parameter', r)}>
                              Chỉnh tham số
                            </button>
                          ),
                        },
                      ]
                    : tab === 'workflows'
                      ? [
                          { key: 'entityType', label: 'Chứng từ' },
                          { key: 'step', label: 'Cấp' },
                          { key: 'role', label: 'Vai trò / quyền' },
                          { key: 'minAmount', label: 'Từ giá trị' },
                          { key: 'slaHours', label: 'SLA giờ' },
                        ]
                      : tab === 'audit'
                        ? [
                            { key: 'created_at', label: 'Lúc' },
                            { key: 'user_id', label: 'Người' },
                            { key: 'entity_type', label: 'Đối tượng' },
                            { key: 'entity_id', label: 'Mã' },
                            { key: 'action', label: 'Thao tác' },
                          ]
                        : tab === 'jobs'
                          ? [
                              { key: 'name', label: 'Job' },
                              { key: 'started_at', label: 'Bắt đầu' },
                              { key: 'finished_at', label: 'Kết thúc' },
                              { key: 'record_count', label: 'Số bản ghi' },
                              { key: 'error_message', label: 'Lỗi' },
                            ]
                          : [
                              { key: 'event_type', label: 'Sự kiện' },
                              { key: 'status', label: 'Trạng thái' },
                              { key: 'attempts', label: 'Số lần thử' },
                              { key: 'last_error', label: 'Lỗi' },
                              {
                                key: 'action',
                                label: 'Thử lại',
                                render: (r: RecordData) =>
                                  r.status === 'DEAD' ? (
                                    <button className="btn" onClick={() => open('retry-outbox', r)}>
                                      Gửi lại
                                    </button>
                                  ) : null,
                              },
                            ];
  const inventoryColumns =
    tab === 'products'
      ? [
          { key: 'code', label: 'Mã' },
          { key: 'name', label: 'Sản phẩm' },
          { key: 'category', label: 'Danh mục' },
          { key: 'baseUnit', label: 'Đơn vị cơ sở' },
          { key: 'minOrder', label: 'Tối thiểu' },
        ]
      : tab === 'prices'
        ? [
            { key: 'skuId', label: 'Sản phẩm' },
            { key: 'nppId', label: 'NPP (0 = chung)' },
            { key: 'uom', label: 'Đơn vị' },
            { key: 'price', label: 'Giá', render: (r: RecordData) => money(numberValue(r.price)) },
            { key: 'validFrom', label: 'Từ' },
            { key: 'validTo', label: 'Đến' },
          ]
        : [
            { key: 'id', label: 'Phiếu' },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r: RecordData) => <Badge status={textValue(r.status)} />,
            },
            { key: 'reason', label: 'Lý do' },
            { key: 'createdBy', label: 'Người đếm' },
            {
              key: 'action',
              label: 'Duyệt',
              render: (r: RecordData) =>
                r.status === 'PENDING_APPROVAL' && has('WAREHOUSE_WRITE') ? (
                  <button className="btn" onClick={() => open('approve-stocktake', r)}>
                    Duyệt kiểm kê
                  </button>
                ) : null,
            },
          ];
  return (
    <>
      <PageHead title={titles[page]}>
        {page === 'settings' && tab === 'workflows' && has('USER_MANAGE') && (
          <button className="btn" onClick={() => open('workflow')}>
            Cấu hình workflow
          </button>
        )}
        {page === 'settings' && tab === 'roles' && has('USER_MANAGE') && (
          <button className="btn" onClick={() => open('role')}>
            Tạo vai trò tùy chỉnh
          </button>
        )}
        {page === 'inventory' && has('WAREHOUSE_WRITE') && (
          <button className="btn" onClick={() => open('transfer')}>
            Điều chuyển kho
          </button>
        )}
        {page === 'partners' && has('USER_MANAGE') && (
          <button className="btn primary" onClick={() => open('partner')}>
            Thêm nhà phân phối
          </button>
        )}
        {page === 'inventory' && has('WAREHOUSE_WRITE') && (
          <button className="btn primary" onClick={() => open('receipt')}>
            Nhập kho
          </button>
        )}
        {page === 'inventory' && has('CATALOG_WRITE') && (
          <button className="btn" onClick={() => open('product')}>
            Thêm sản phẩm
          </button>
        )}
        {page === 'inventory' && has('POLICY_WRITE') && (
          <button className="btn" onClick={() => open('price')}>
            Phát hành giá
          </button>
        )}
        {page === 'routes' && has('ROUTE_WRITE') && (
          <>
            <button className="btn primary" onClick={() => open('route')}>
              Tạo tuyến
            </button>
            <button className="btn" onClick={() => open('staff')}>
              Thêm nhân viên
            </button>
            <button className="btn" onClick={() => open('handover')}>
              Bàn giao
            </button>
          </>
        )}
        {page === 'policies' && has('POLICY_WRITE') && (
          <button className="btn primary" onClick={() => open('policy')}>
            Soạn chương trình
          </button>
        )}
        {page === 'tickets' && has('TICKET_WRITE') && (
          <button className="btn primary" onClick={() => open('ticket')}>
            Tạo yêu cầu
          </button>
        )}
        {page === 'settings' && has('USER_MANAGE') && (
          <button className="btn primary" onClick={() => open('user')}>
            Tạo tài khoản
          </button>
        )}
      </PageHead>
      {page === 'settings' && (
        <div className="tabs">
          {[
            ['users', 'Tài khoản'],
            ['roles', 'Vai trò'],
            ['audit', 'Nhật ký'],
            ['jobs', 'Lịch chạy'],
            ['outbox', 'Sự kiện'],
            ['parameters', 'Tham số'],
            ['workflows', 'Workflow'],
          ].map(([value, title]) => (
            <button
              className={tab === value ? 'active' : ''}
              key={value}
              onClick={() => setTab(value)}
            >
              {title}
            </button>
          ))}
        </div>
      )}
      {page === 'inventory' && (
        <>
          <div className="tabs">
            {[
              ['stock', 'Tồn theo lô'],
              ['products', 'Sản phẩm'],
              ['prices', 'Bảng giá'],
              ['stocktakes', 'Kiểm kê'],
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
          <label>
            Kho
            <select value={warehouseId} onChange={(e) => setWarehouse(e.target.value)}>
              {warehouses.data.map((w) => (
                <option key={textValue(w.id)} value={textValue(w.id)}>
                  {textValue(w.name)} · {textValue(w.type)}
                </option>
              ))}
            </select>
          </label>
        </>
      )}
      {page === 'routes' && (
        <label>
          Nhà phân phối
          <select
            value={nppId}
            disabled={Boolean(user?.nppId)}
            onChange={(e) => setNpp(Number(e.target.value))}
          >
            {db.partners.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
      )}
      <LoadState {...state} retry={state.reload} />
      <Panel>
        <div className="table-toolbar">
          <SearchBox value={query} onChange={setQuery} />
          {role !== 'npp' && (
            <button className="btn" onClick={state.reload}>
              Làm mới
            </button>
          )}
        </div>
        <Table
          onRow={page === 'tickets' ? (r) => setTicketId(textValue(r.id)) : undefined}
          rows={state.data.filter((r) =>
            JSON.stringify(r).toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')),
          )}
          searchText={query}
          columns={(page === 'inventory' && tab !== 'stock' ? inventoryColumns : columns).map(
            (c) => ({
              ...c,
              render: c.render || ((r: RecordData) => textValue(r[c.key])),
            }),
          )}
        />
      </Panel>
      {page === 'policies' && <Documents />}
      {page === 'tickets' && ticketId && <TicketCase id={ticketId} />}
      {page === 'routes' && (
        <Panel title="Nhân viên trong NPP">
          <Table
            rows={staff.data}
            columns={[
              { key: 'name', label: 'Họ tên', render: (r) => textValue(r.name) },
              { key: 'role', label: 'Vai trò', render: (r) => textValue(r.role) },
            ]}
          />
        </Panel>
      )}
      {action && (
        <>
          <ActionForm
            title={
              {
                partner: 'Thêm nhà phân phối',
                receipt: 'Phiếu nhập kho',
                transfer: 'Điều chuyển kho',
                stocktake: 'Kiểm kê lô',
                staff: 'Thêm nhân viên',
                user: 'Tạo tài khoản',
                access: 'Cập nhật quyền',
                route: 'Tạo tuyến',
                plan: 'Sinh lượt ghé',
                handover: 'Bàn giao tuyến',
                policy: 'Chương trình ưu đãi',
                publish: 'Phát hành chính sách',
                ticket: 'Tạo yêu cầu',
                'ticket-state': 'Xử lý yêu cầu',
              }[action] || action
            }
            intro={
              action === 'workspace' ? (
                <Panel title={`Phạm vi đang cấp cho ${selected.name}`}>
                  <Table
                    rows={grants.data}
                    columns={[
                      { key: 'roleCode', label: 'Vai trò', render: (r) => textValue(r.roleCode) },
                      { key: 'scopeType', label: 'Loại', render: (r) => textValue(r.scopeType) },
                      { key: 'scopeId', label: 'Phạm vi', render: (r) => textValue(r.scopeId) },
                    ]}
                  />
                </Panel>
              ) : undefined
            }
            fields={fields}
            onClose={() => setAction('')}
            onSubmit={save}
          />
        </>
      )}
    </>
  );
}
