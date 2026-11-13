import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ReceiptText, BookOpen, Undo2, CheckCheck } from 'lucide-react';
import { useStore } from '../../shared/store';
import { command, textValue, numberValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { PageHead, Panel, Table, Badge, SearchBox } from '../../shared/ui';
import { money, today } from '../../shared/utils';
export default function Finance() {
  const { role, has } = useStore();
  return role === 'npp' && !has('CREDIT_WRITE') && !has('CLOSING_WRITE') ? (
    <DistributorFinance />
  ) : (
    <AccountingFinance />
  );
}
function AccountingFinance() {
  const { has, db, role, notify } = useStore();
  const accounts = useApi<RecordData[]>('/credit', []),
    periods = useApi<RecordData[]>('/closing/periods', []),
    returns = useApi<RecordData[]>('/returns', []),
    exceptions = useApi<RecordData[]>('/closing/exceptions', []);
  const adjustments = useApi<RecordData[]>('/closing/adjustments', []);
  const [npp, setNpp] = useState(''),
    [tab, setTab] = useState('accounts'),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const nppId = npp || textValue(accounts.data[0]?.nppId);
  const ledger = useApi<RecordData[]>(nppId ? `/credit/${nppId}/ledger` : null, []),
    invoices = useApi<RecordData[]>(nppId ? `/credit/${nppId}/invoices` : null, []);
  const snapshots = useApi<RecordData[]>(
    selected.id && tab === 'periods' ? `/closing/periods/${selected.id}/snapshots` : null,
    [],
  );
  const options = db.partners.map((p) => ({ value: p.id, label: p.name }));
  let fields: FormField[] = [{ key: 'reason', label: 'Lý do', type: 'textarea' }];
  if (action === 'temporary')
    fields = [
      { key: 'amount', label: 'Hạn mức tạm thời VND', type: 'number', min: 0, step: '0.01' },
      { key: 'validUntil', label: 'Hết hiệu lực (tối đa 31 ngày)', type: 'datetime-local' },
      ...fields,
    ];
  if (action === 'terms')
    fields = [
      {
        key: 'overdueDaysThreshold',
        label: 'Số ngày quá hạn được phép',
        type: 'number',
        min: 0,
        value: 0,
      },
      ...fields,
    ];
  if (action === 'payment')
    fields = [
      { key: 'amount', label: 'Số tiền', type: 'number', min: 0.01, step: '0.01' },
      { key: 'reference', label: 'Mã tham chiếu thanh toán' },
      ...fields,
    ];
  if (action === 'limit')
    fields = [
      { key: 'amount', label: 'Hạn mức mới', type: 'number', min: 0, step: '0.01' },
      ...fields,
    ];
  if (action === 'period')
    fields = [
      {
        key: 'type',
        label: 'Loại kỳ',
        options: ['DAY', 'WEEK', 'MONTH', 'QUARTER'].map((value) => ({ value, label: value })),
      },
      { key: 'startDate', label: 'Từ ngày', type: 'date', value: today() },
      { key: 'endDate', label: 'Đến ngày', type: 'date', value: today() },
    ];
  if (action === 'adjustment')
    fields = [
      {
        key: 'originalPeriodId',
        label: 'Kỳ đã chốt',
        options: periods.data
          .filter((p) => ['CLOSED', 'LOCKED', 'PUBLISHED'].includes(textValue(p.status)))
          .map((p) => ({ value: textValue(p.id), label: `${p.type} ${p.startDate}` })),
      },
      { key: 'nppId', label: 'Nhà phân phối', options },
      {
        key: 'delta',
        label: 'Giá trị điều chỉnh (+ tăng nợ, - giảm nợ)',
        type: 'number',
        step: '0.01',
      },
      ...fields,
    ];
  if (action === 'approve-adjustment')
    fields = [
      {
        key: 'postedPeriodId',
        label: 'Kỳ đang mở để ghi điều chỉnh',
        options: periods.data
          .filter((p) => p.status === 'OPEN')
          .map((p) => ({ value: textValue(p.id), label: `${p.type} ${p.startDate}` })),
      },
    ];
  if (action === 'transition')
    fields = [
      {
        key: 'status',
        label: 'Bước tiếp theo',
        options: (
          {
            OPEN: ['PRE_CLOSE'],
            PRE_CLOSE: ['OPEN', 'CLOSED'],
            CLOSED: ['LOCKED'],
            LOCKED: ['PUBLISHED'],
          }[textValue(selected.status)] || []
        ).map((value) => ({ value, label: value })),
      },
      ...fields,
    ];
  async function submit(values: Record<string, string>) {
    if (action === 'temporary')
      await command(`/credit/${nppId}/temporary-limit`, {
        ...values,
        amount: Number(values.amount),
        validUntil: new Date(values.validUntil).toISOString(),
      });
    if (action === 'terms')
      await command(`/credit/${nppId}/terms`, {
        ...values,
        overdueDaysThreshold: Number(values.overdueDaysThreshold),
      });
    if (action === 'approve-adjustment')
      await command(`/closing/adjustments/${selected.id}/approve`, {
        postedPeriodId: Number(values.postedPeriodId),
      });
    if (action === 'payment')
      await command('/credit/payments', {
        nppId: Number(nppId),
        amount: Number(values.amount),
        reference: values.reference,
      });
    if (action === 'limit')
      await command(`/credit/${nppId}/limit`, {
        amount: Number(values.amount),
        reason: values.reason,
      });
    if (action === 'period') await command('/closing/periods', values);
    if (action === 'transition')
      await command(`/closing/periods/${selected.id}/transition`, values);
    if (action === 'adjustment')
      await command('/closing/adjustments', {
        ...values,
        originalPeriodId: Number(values.originalPeriodId),
        nppId: Number(values.nppId),
        delta: Number(values.delta),
      });
    if (action === 'approve-return') await command(`/returns/${selected.id}/approve`, values);
    if (action === 'receive-return') await command(`/returns/${selected.id}/receive`);
    if (action === 'resolve') await command(`/closing/exceptions/${selected.id}/resolve`, values);
    notify('Đã ghi nhận trên máy chủ.');
    accounts.reload();
    ledger.reload();
    invoices.reload();
    periods.reload();
    returns.reload();
    exceptions.reload();
    snapshots.reload();
    adjustments.reload();
  }
  const open = (name: string, item: RecordData = {}) => {
    setSelected(item);
    setAction(name);
  };
  return (
    <>
      <PageHead title="Công nợ & đối soát">
        {has('CREDIT_WRITE') && (
          <>
            <button className="btn" onClick={() => open('temporary')}>
              Nâng hạn mức tạm
            </button>
            <button className="btn" onClick={() => open('terms')}>
              Ngưỡng quá hạn
            </button>
            <button className="btn primary" onClick={() => open('payment')}>
              Ghi nhận thanh toán
            </button>
          </>
        )}
        {has('CLOSING_WRITE') && (
          <>
            <button className="btn" onClick={() => open('period')}>
              Tạo kỳ
            </button>
            <button className="btn" onClick={() => open('adjustment')}>
              Điều chỉnh sau chốt
            </button>
          </>
        )}
      </PageHead>
      <div className="tabs">
        {[
          ['accounts', 'Công nợ'],
          ['periods', 'Kỳ đối soát'],
          ['returns', 'Đổi trả'],
          ['exceptions', 'Ngoại lệ'],
          ['adjustments', 'Điều chỉnh'],
        ].map(([value, label]) => (
          <button
            key={value}
            className={tab === value ? 'active' : ''}
            onClick={() => {
              setTab(value);
              setSelected({});
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === 'accounts' && (
        <>
          <LoadState {...accounts} retry={accounts.reload} />
          <Panel>
            <Table
              rows={accounts.data}
              onRow={(r) => setNpp(textValue(r.nppId))}
              columns={[
                {
                  key: 'npp',
                  label: 'Nhà phân phối',
                  render: (r) =>
                    db.partners.find((p) => p.id === textValue(r.nppId))?.name ||
                    textValue(r.nppId),
                },
                {
                  key: 'limit',
                  label: 'Hạn mức',
                  render: (r) => money(numberValue(r.limitAmount)),
                },
                { key: 'balance', label: 'Dư nợ', render: (r) => money(numberValue(r.balance)) },
                {
                  key: 'overdue',
                  label: 'Quá hạn',
                  render: (r) => money(numberValue(r.overdueAmount)),
                },
              ]}
            />
          </Panel>
          {nppId && (
            <Panel
              title="Sổ công nợ"
              className="npp-detail-surface"
              action={
                has('CREDIT_WRITE') && (
                  <button className="btn" onClick={() => open('limit')}>
                    Thay hạn mức
                  </button>
                )
              }
            >
              <LoadState {...ledger} retry={ledger.reload} />
              <Table
                rows={ledger.data}
                columns={[
                  { key: 'docType', label: 'Chứng từ', render: (r) => textValue(r.docType) },
                  { key: 'docId', label: 'Số', render: (r) => textValue(r.docId) },
                  { key: 'debit', label: 'Ghi nợ', render: (r) => money(numberValue(r.debit)) },
                  { key: 'credit', label: 'Ghi có', render: (r) => money(numberValue(r.credit)) },
                  {
                    key: 'balanceAfter',
                    label: 'Dư nợ sau',
                    render: (r) => money(numberValue(r.balanceAfter)),
                  },
                  { key: 'postedAt', label: 'Thời điểm', render: (r) => textValue(r.postedAt) },
                ]}
              />
            </Panel>
          )}
          {nppId && (
            <Panel title="Hóa đơn" className="npp-invoice-surface">
              <Table
                rows={invoices.data}
                columns={[
                  { key: 'id', label: 'Hóa đơn', render: (r) => textValue(r.id) },
                  { key: 'amount', label: 'Giá trị', render: (r) => money(numberValue(r.amount)) },
                  {
                    key: 'remaining',
                    label: role === 'npp' ? 'Còn phải trả' : 'Còn phải thu',
                    render: (r) => money(numberValue(r.remaining)),
                  },
                  { key: 'dueDate', label: 'Hạn trả', render: (r) => textValue(r.dueDate) },
                  {
                    key: 'status',
                    label: 'Trạng thái',
                    render: (r) => <Badge status={textValue(r.status)} />,
                  },
                ]}
              />
            </Panel>
          )}
        </>
      )}
      {tab === 'adjustments' && (
        <Panel title="Phiếu điều chỉnh sau chốt">
          <LoadState {...adjustments} retry={adjustments.reload} />
          <Table
            rows={adjustments.data}
            columns={[
              { key: 'id', label: 'Phiếu', render: (r) => textValue(r.id) },
              {
                key: 'originalPeriodId',
                label: 'Kỳ gốc',
                render: (r) => textValue(r.originalPeriodId),
              },
              { key: 'nppId', label: 'NPP', render: (r) => textValue(r.nppId) },
              { key: 'delta', label: 'Điều chỉnh', render: (r) => money(numberValue(r.delta)) },
              {
                key: 'status',
                label: 'Trạng thái',
                render: (r) => <Badge status={textValue(r.status)} />,
              },
              {
                key: 'action',
                label: 'Duyệt',
                render: (r) =>
                  r.status !== 'APPROVED' && has('CLOSING_WRITE') ? (
                    <button className="btn" onClick={() => open('approve-adjustment', r)}>
                      Duyệt vào kỳ mở
                    </button>
                  ) : null,
              },
            ]}
          />
        </Panel>
      )}
      {tab === 'periods' && (
        <>
          <LoadState {...periods} retry={periods.reload} />
          <Panel title="Các kỳ báo cáo">
            <Table
              rows={periods.data}
              onRow={setSelected}
              columns={[
                { key: 'type', label: 'Loại', render: (r) => textValue(r.type) },
                { key: 'from', label: 'Từ', render: (r) => textValue(r.startDate) },
                { key: 'to', label: 'Đến', render: (r) => textValue(r.endDate) },
                {
                  key: 'status',
                  label: 'Trạng thái',
                  render: (r) => <Badge status={textValue(r.status)} />,
                },
                {
                  key: 'action',
                  label: 'Xử lý',
                  render: (r) =>
                    has('CLOSING_WRITE') && r.status !== 'PUBLISHED' ? (
                      <button
                        className="btn"
                        onClick={(e) => {
                          e.stopPropagation();
                          open('transition', r);
                        }}
                      >
                        Chuyển kỳ
                      </button>
                    ) : null,
                },
              ]}
            />
          </Panel>
          {Boolean(selected.id) && (
            <Panel title="Số liệu đã chốt" className="npp-detail-surface">
              <LoadState {...snapshots} retry={snapshots.reload} />
              <Table
                rows={snapshots.data}
                columns={[
                  { key: 'nppId', label: 'NPP', render: (r) => textValue(r.nppId) },
                  {
                    key: 'amount',
                    label: 'Doanh số đã chốt',
                    render: (r) => money(numberValue(r.amount)),
                  },
                  {
                    key: 'debt',
                    label: 'Công nợ tại chốt',
                    render: (r) => money(numberValue(r.debt)),
                  },
                  { key: 'capturedAt', label: 'Chụp lúc', render: (r) => textValue(r.capturedAt) },
                ]}
              />
            </Panel>
          )}
        </>
      )}
      {tab === 'returns' && (
        <>
          <LoadState {...returns} retry={returns.reload} />
          <Panel title="Phiếu hoàn hàng">
            <Table
              rows={returns.data}
              columns={[
                { key: 'id', label: 'Số phiếu', render: (r) => textValue(r.id) },
                { key: 'orderId', label: 'Đơn nguồn', render: (r) => textValue(r.orderId) },
                { key: 'amount', label: 'Giá trị', render: (r) => money(numberValue(r.amount)) },
                {
                  key: 'status',
                  label: 'Trạng thái',
                  render: (r) => <Badge status={textValue(r.status)} />,
                },
                {
                  key: 'action',
                  label: 'Xử lý',
                  render: (r) =>
                    has('ORDER_APPROVE') && ['DRAFT', 'APPROVED'].includes(textValue(r.status)) ? (
                      <button
                        className="btn"
                        onClick={() =>
                          open(r.status === 'DRAFT' ? 'approve-return' : 'receive-return', r)
                        }
                      >
                        {r.status === 'DRAFT' ? 'Duyệt' : 'Nhận vào kho cách ly'}
                      </button>
                    ) : null,
                },
              ]}
            />
          </Panel>
        </>
      )}
      {tab === 'exceptions' && (
        <>
          <LoadState {...exceptions} retry={exceptions.reload} />
          <Panel title="Ngoại lệ đối soát">
            <Table
              rows={exceptions.data}
              columns={[
                { key: 'id', label: 'Mã', render: (r) => textValue(r.id) },
                { key: 'orderId', label: 'Đơn', render: (r) => textValue(r.orderId) },
                {
                  key: 'reason',
                  label: 'Mô tả',
                  render: (r) => textValue(r.reason || r.description),
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
                    has('CLOSING_WRITE') && r.status === 'OPEN' ? (
                      <button className="btn" onClick={() => open('resolve', r)}>
                        Giải quyết
                      </button>
                    ) : null,
                },
              ]}
            />
          </Panel>
        </>
      )}
      {action && (
        <ActionForm
          title={
            {
              payment: 'Ghi nhận thanh toán',
              limit: 'Thay hạn mức',
              period: 'Tạo kỳ',
              transition: 'Chuyển trạng thái kỳ',
              adjustment: 'Phiếu điều chỉnh',
              resolve: 'Xử lý ngoại lệ',
              'approve-return': 'Duyệt hoàn hàng',
              'receive-return': 'Nhận hàng hoàn',
            }[action] || action
          }
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={submit}
        />
      )}
    </>
  );
}

function DistributorFinance() {
  const { user } = useStore();
  const [view, setView] = useState('invoices'),
    [query, setQuery] = useState(''),
    [openOnly, setOpenOnly] = useState(true);
  const accounts = useApi<RecordData[]>('/credit', []);
  const account = accounts.data.find((a) => String(a.nppId) === String(user?.nppId));
  const nppId = account?.nppId;
  const invoices = useApi<RecordData[]>(nppId ? `/credit/${nppId}/invoices` : null, []);
  const ledger = useApi<RecordData[]>(
    view === 'ledger' && nppId ? `/credit/${nppId}/ledger` : null,
    [],
  );
  const returns = useApi<RecordData[]>(view === 'returns' ? '/returns' : null, []);
  const current = view === 'invoices' ? invoices : view === 'ledger' ? ledger : returns;
  const rows = current.data
    .filter((r) => view !== 'invoices' || !openOnly || numberValue(r.remaining) > 0)
    .filter((r) =>
      JSON.stringify(r).toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')),
    );
  const date = (value: unknown) =>
    value ? new Date(textValue(value)).toLocaleDateString('vi-VN') : '—';
  const documents: Record<string, string> = {
    INVOICE: 'Hóa đơn',
    PAYMENT: 'Thanh toán',
    RETURN: 'Hoàn hàng',
    ADJUSTMENT: 'Điều chỉnh',
  };
  return (
    <div className="distributor-finance">
      <PageHead title="Công nợ với NSX">
        <Link className="btn" to="/tickets">
          Gửi yêu cầu đối chiếu
          <ArrowRight size={15} />
        </Link>
      </PageHead>
      <LoadState {...accounts} retry={accounts.reload} />
      <section className="finance-balance-band" aria-label="Số dư công nợ">
        <div>
          <span>Còn phải trả NSX</span>
          <strong>{account ? money(numberValue(account.balance)) : '—'}</strong>
        </div>
        <div>
          <span>Đã quá hạn</span>
          <strong className={numberValue(account?.overdueAmount) > 0 ? 'is-overdue' : ''}>
            {account ? money(numberValue(account.overdueAmount)) : '—'}
          </strong>
        </div>
        <div>
          <span>Hạn mức được cấp</span>
          <strong>{account ? money(numberValue(account.limitAmount)) : '—'}</strong>
        </div>
      </section>
      <nav className="finance-view-switch" aria-label="Hồ sơ công nợ">
        {[
          { id: 'invoices', title: 'Hóa đơn phải trả', icon: ReceiptText },
          { id: 'ledger', title: 'Lịch sử phát sinh', icon: BookOpen },
          { id: 'returns', title: 'Phiếu đổi trả', icon: Undo2 },
        ].map(({ id, title, icon: Icon }) => (
          <button
            key={id}
            className={view === id ? 'active' : ''}
            aria-pressed={view === id}
            onClick={() => {
              setView(id);
              setQuery('');
            }}
          >
            <Icon size={18} />
            {title}
          </button>
        ))}
      </nav>
      <Panel
        title={
          view === 'invoices'
            ? 'Hóa đơn phải trả'
            : view === 'ledger'
              ? 'Lịch sử phát sinh'
              : 'Phiếu đổi trả'
        }
        className="finance-ledger-panel"
        action={
          view === 'invoices' ? (
            <label className="finance-open-filter">
              <input
                type="checkbox"
                checked={openOnly}
                onChange={(e) => setOpenOnly(e.target.checked)}
              />
              Chưa trả hết
            </label>
          ) : undefined
        }
      >
        <LoadState {...current} retry={current.reload} />
        {current.data.length > 0 && (
          <div className="table-toolbar">
            <SearchBox placeholder="Tìm chứng từ..." value={query} onChange={setQuery} />
          </div>
        )}
        {!current.loading && !current.error && !rows.length ? (
          <div className="finance-empty">
            <CheckCheck size={25} />
            <span>
              {query
                ? 'Không có chứng từ phù hợp'
                : view === 'invoices'
                  ? 'Không có hóa đơn chờ thanh toán'
                  : view === 'ledger'
                    ? 'Chưa có phát sinh'
                    : 'Chưa có phiếu đổi trả'}
            </span>
          </div>
        ) : (
          <Table<RecordData>
            rows={rows}
            searchText={query}
            columns={
              view === 'invoices'
                ? [
                    {
                      key: 'id',
                      label: 'Hóa đơn',
                      render: (r) => <strong>HĐ-{textValue(r.id)}</strong>,
                    },
                    { key: 'dueDate', label: 'Hạn trả', render: (r) => date(r.dueDate) },
                    {
                      key: 'amount',
                      label: 'Giá trị',
                      render: (r) => money(numberValue(r.amount)),
                    },
                    {
                      key: 'remaining',
                      label: 'Còn phải trả',
                      render: (r) => <strong>{money(numberValue(r.remaining))}</strong>,
                    },
                    {
                      key: 'status',
                      label: 'Trạng thái',
                      render: (r) => <Badge status={textValue(r.status)} />,
                    },
                  ]
                : view === 'ledger'
                  ? [
                      {
                        key: 'docType',
                        label: 'Chứng từ',
                        render: (r) => (
                          <span>
                            {documents[textValue(r.docType)] || textValue(r.docType)} #
                            {textValue(r.docId)}
                          </span>
                        ),
                      },
                      { key: 'postedAt', label: 'Ngày ghi nhận', render: (r) => date(r.postedAt) },
                      {
                        key: 'debit',
                        label: 'Tăng công nợ',
                        render: (r) => (numberValue(r.debit) ? money(numberValue(r.debit)) : '—'),
                      },
                      {
                        key: 'credit',
                        label: 'Giảm công nợ',
                        render: (r) => (numberValue(r.credit) ? money(numberValue(r.credit)) : '—'),
                      },
                      {
                        key: 'balanceAfter',
                        label: 'Dư nợ sau',
                        render: (r) => money(numberValue(r.balanceAfter)),
                      },
                    ]
                  : [
                      {
                        key: 'id',
                        label: 'Phiếu hoàn',
                        render: (r) => <strong>TH-{textValue(r.id)}</strong>,
                      },
                      {
                        key: 'orderId',
                        label: 'Đơn nguồn',
                        render: (r) => (
                          <Link
                            className="text-button"
                            to={`/orders?channel=sellin&order=${r.orderId}`}
                          >
                            Đơn #{textValue(r.orderId)}
                            <ArrowRight size={14} />
                          </Link>
                        ),
                      },
                      {
                        key: 'amount',
                        label: 'Giá trị',
                        render: (r) => money(numberValue(r.amount)),
                      },
                      {
                        key: 'status',
                        label: 'Trạng thái',
                        render: (r) => <Badge status={textValue(r.status)} />,
                      },
                    ]
            }
          />
        )}
      </Panel>
    </div>
  );
}
