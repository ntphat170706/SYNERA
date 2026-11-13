import { useState } from 'react';
import { useStore } from '../../shared/store';
import { command, numberValue, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { PageHead, Panel, Table, Badge } from '../../shared/ui';
import { today, money } from '../../shared/utils';
import { Handshake, MessageSquareText } from 'lucide-react';
export default function Trade() {
  const { db, user, role, products, has, notify } = useStore();
  const distributor = role === 'npp' && !has('TRADE_APPROVE') && !has('CREDIT_WRITE');
  const canBudget = has('SELLIN_CREATE') || has('TRADE_APPROVE') || has('CREDIT_WRITE');
  const [tab, setTab] = useState(canBudget ? 'requests' : 'feedback'),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const data = useApi<RecordData[]>(`/trade/${tab}`, []),
    budgets = useApi<RecordData[]>(
      canBudget && (!distributor || action === 'request') ? '/trade/budgets' : null,
      [],
    );
  const partners = {
    key: 'nppId',
    label: 'NPP',
    value: user?.nppId,
    options: db.partners.map((p) => ({ value: p.id, label: p.name })),
  };
  const reason = { key: 'reason', label: 'Lý do', type: 'textarea' };
  const fields: FormField[] =
    action === 'budget'
      ? [
          { key: 'name', label: 'Tên ngân sách' },
          { key: 'amount', label: 'Ngân sách VND', type: 'number', min: 1 },
          { key: 'from', label: 'Từ', type: 'date', value: today() },
          { key: 'to', label: 'Đến', type: 'date', value: today() },
        ]
      : action === 'request'
        ? [
            ...(!distributor ? [partners] : []),
            {
              key: 'budgetId',
              label: 'Ngân sách',
              options: budgets.data.map((b) => ({
                value: textValue(b.id),
                label: `${b.name} · ${money(numberValue(b.available))}`,
              })),
            },
            { key: 'title', label: 'Nội dung đề xuất' },
            { key: 'amount', label: 'Số tiền đề xuất', type: 'number', min: 1 },
          ]
        : action === 'feedback'
          ? [
              ...(!distributor ? [partners] : []),
              {
                key: 'skuId',
                label: 'Sản phẩm',
                options: products.map((p) => ({ value: p.id, label: p.name })),
              },
              { key: 'topic', label: 'Chủ đề' },
              { key: 'description', label: 'Nội dung', type: 'textarea' },
            ]
          : action === 'settle'
            ? [
                { key: 'amount', label: 'Thực chi VND', type: 'number', min: 0 },
                { key: 'referenceNo', label: 'Số chứng từ' },
                {
                  key: 'attachmentId',
                  label: 'Chứng từ thực chi',
                  type: 'attachment',
                  nppId: Number(selected.nppId),
                },
                reason,
              ]
            : [
                {
                  key: 'approvedAmount',
                  label: 'Số tiền duyệt (0 = từ chối)',
                  type: 'number',
                  value: Number(selected.approved) || Number(selected.requested),
                  min: 0,
                },
                reason,
              ];
  return (
    <>
      <PageHead title={distributor ? 'Hỗ trợ bán hàng' : 'Trade marketing'}>
        {has('TRADE_APPROVE') && (
          <button className="btn" onClick={() => setAction('budget')}>
            Tạo ngân sách
          </button>
        )}
        {has('SELLIN_CREATE') && (
          <button className="btn primary" onClick={() => setAction('request')}>
            Đề xuất hỗ trợ
          </button>
        )}
        {has('TICKET_WRITE') && (
          <button className="btn" onClick={() => setAction('feedback')}>
            Phản hồi thị trường
          </button>
        )}
      </PageHead>
      <div className="tabs" aria-label="Hỗ trợ bán hàng">
        {[
          ['requests', 'Đề xuất'],
          ['budgets', 'Ngân sách'],
          ['feedback', 'Phản hồi'],
          ['settlements', 'Quyết toán'],
        ]
          .filter(([value]) =>
            distributor
              ? value === 'requests' || value === 'feedback'
              : canBudget || value === 'feedback',
          )
          .map(([value, label]) => (
            <button
              key={value}
              className={tab === value ? 'active' : ''}
              onClick={() => setTab(value)}
            >
              {label}
            </button>
          ))}
      </div>
      <LoadState {...data} retry={data.reload} />
      <Panel className="trade-record-panel">
        {distributor && !data.loading && !data.error && !data.data.length ? (
          <div className="trade-empty">
            {tab === 'requests' ? <Handshake size={30} /> : <MessageSquareText size={30} />}
            <h3>{tab === 'requests' ? 'Chưa có đề xuất hỗ trợ' : 'Chưa có phản hồi thị trường'}</h3>
            {((tab === 'requests' && has('SELLIN_CREATE')) ||
              (tab === 'feedback' && has('TICKET_WRITE'))) && (
              <button
                className="text-button"
                onClick={() => setAction(tab === 'requests' ? 'request' : 'feedback')}
              >
                {tab === 'requests' ? 'Tạo đề xuất' : 'Gửi phản hồi'}
              </button>
            )}
          </div>
        ) : (
          <Table
            rows={data.data}
            columns={(tab === 'requests'
              ? [
                  { key: 'id', label: 'Mã' },
                  { key: 'title', label: 'Nội dung' },
                  ...(!distributor
                    ? [
                        {
                          key: 'nppId',
                          label: 'NPP',
                          render: (r: RecordData) =>
                            db.partners.find((p) => p.id === String(r.nppId))?.name ||
                            textValue(r.nppId),
                        },
                      ]
                    : []),
                  {
                    key: 'requested',
                    label: 'Đề xuất',
                    render: (r: RecordData) => money(numberValue(r.requested)),
                  },
                  {
                    key: 'approved',
                    label: 'Đã duyệt',
                    render: (r: RecordData) => money(numberValue(r.approved)),
                  },
                  {
                    key: 'status',
                    label: 'Trạng thái',
                    render: (r: RecordData) => <Badge status={textValue(r.status)} />,
                  },
                  ...(!distributor
                    ? [
                        {
                          key: 'action',
                          label: 'Xử lý',
                          render: (r: RecordData) => (
                            <>
                              {has('TRADE_APPROVE') &&
                                ['SUBMITTED', 'PENDING_APPROVAL'].includes(textValue(r.status)) && (
                                  <button
                                    className="btn"
                                    onClick={() => {
                                      setSelected(r);
                                      setAction('decision');
                                    }}
                                  >
                                    Duyệt / từ chối
                                  </button>
                                )}
                              {has('CREDIT_WRITE') &&
                                ['APPROVED', 'PARTIALLY_APPROVED'].includes(
                                  textValue(r.status),
                                ) && (
                                  <button
                                    className="btn"
                                    onClick={() => {
                                      setSelected(r);
                                      setAction('settle');
                                    }}
                                  >
                                    Quyết toán
                                  </button>
                                )}
                            </>
                          ),
                        },
                      ]
                    : []),
                ]
              : tab === 'budgets'
                ? [
                    { key: 'name', label: 'Ngân sách' },
                    { key: 'amount', label: 'Tổng' },
                    { key: 'committed', label: 'Đã cam kết' },
                    { key: 'available', label: 'Khả dụng' },
                  ]
                : tab === 'feedback'
                  ? [
                      {
                        key: 'skuId',
                        label: 'Sản phẩm',
                        render: (r: RecordData) =>
                          products.find((p) => p.id === String(r.skuId))?.name ||
                          textValue(r.skuId),
                      },
                      { key: 'topic', label: 'Chủ đề' },
                      { key: 'description', label: 'Nội dung' },
                      { key: 'ticketId', label: 'Ticket' },
                      {
                        key: 'action',
                        label: 'Xử lý',
                        render: (r: RecordData) =>
                          !r.ticketId && has('TICKET_WRITE') ? (
                            <button
                              className="btn"
                              onClick={() =>
                                void command(`/trade/feedback/${r.id}/ticket`)
                                  .then(data.reload)
                                  .catch((e) => notify(e.message))
                              }
                            >
                              Yêu cầu xử lý
                            </button>
                          ) : null,
                      },
                    ]
                  : [
                      { key: 'id', label: 'Mã' },
                      { key: 'request_id', label: 'Đề xuất' },
                      { key: 'amount', label: 'Thực chi' },
                      { key: 'reference_no', label: 'Chứng từ' },
                    ]
            ).map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
          />
        )}
      </Panel>
      {action && (
        <ActionForm
          title={
            {
              request: 'Đề xuất hỗ trợ',
              feedback: 'Phản hồi thị trường',
              budget: 'Tạo ngân sách',
              settle: 'Quyết toán hỗ trợ',
              decision: 'Duyệt đề xuất',
            }[action] || 'Hỗ trợ bán hàng'
          }
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            const path =
              action === 'budget'
                ? '/trade/budgets'
                : action === 'request'
                  ? '/trade/requests'
                  : action === 'feedback'
                    ? '/trade/feedback'
                    : `/trade/requests/${selected.id}/${action === 'settle' ? 'settle' : 'decide'}`;
            const body: RecordData = { ...values, ...(distributor ? { nppId: user?.nppId } : {}) };
            for (const k of [
              'amount',
              'approvedAmount',
              'skuId',
              'budgetId',
              'nppId',
              'attachmentId',
            ])
              if (values[k]) body[k] = Number(values[k]);
            await command(path, body);
            data.reload();
            budgets.reload();
            notify('Đã lưu giao dịch.');
          }}
        />
      )}
    </>
  );
}
