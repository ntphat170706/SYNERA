import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowRight, Truck, MessageSquare } from 'lucide-react';
import { useStore } from '../../shared/store';
import { useApi } from '../../shared/hooks/useApi';
import { command, records, numberValue, textValue, type RecordData } from '../../shared/api';
import { PageHead, Panel, Table, Badge } from '../../shared/ui';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { money } from '../../shared/utils';
const labels: Record<string, string> = {
  approve: 'Duyệt và giữ hàng',
  ship: 'Xuất giao bù',
  receipt: 'Ghi thực nhận',
  cancel: 'Hủy đề xuất',
  create: 'Đề xuất giao bù',
  feedback: 'Ghi kết quả báo khách',
};
export default function Workflow() {
  const { db, products, user, has, notify, role } = useStore();
  const [params, setParams] = useSearchParams();
  const [orderId, setOrderId] = useState(params.get('order') || '');
  const [action, setAction] = useState('');
  const selected = params.get('replacement') || '';
  const setSelected = (id: string) => setParams(id ? { replacement: id } : {});
  const sales = useApi<RecordData[]>('/sales-orders', []),
    cases = useApi<RecordData[]>('/sales-replacements', []);
  const source = useApi<RecordData>(orderId ? `/sales-orders/${orderId}` : null, {});
  const detail = useApi<RecordData>(selected ? `/sales-replacements/${selected}` : null, {});
  const staffNpp = user?.nppId || Number(detail.data.nppId) || Number(source.data.nppId);
  const staff = useApi<RecordData[]>(staffNpp ? `/staff?nppId=${staffNpp}` : null, []);
  const person = (id: unknown) =>
    textValue(staff.data.find((s) => String(s.id) === String(id))?.name) || 'Người được phân công';
  const item = detail.data,
    caseLines = records(item.lines);
  const sourceLines = records(source.data.lines).filter(
    (l) => numberValue(l.qtyAvailableToReplace) > 0,
  );
  const productName = (id: unknown) =>
    products.find((p) => p.id === String(id))?.name || `SKU ${id}`;
  const proposed = cases.data.filter((c) => c.status === 'PROPOSED').length;
  const feedback = cases.data.filter((c) => !c.notifiedAt).length;
  let fields: FormField[] = [{ key: 'reason', label: 'Nội dung xử lý', type: 'textarea' }];
  if (action === 'create')
    fields = [
      ...sourceLines.map((l) => ({
        key: `qty_${l.id}`,
        label: `${db.stores.find((s) => s.id === String(source.data.storeId))?.name || 'Điểm bán'} · SKU ${l.skuId} (${l.uom})`,
        type: 'number' as const,
        min: 0,
        step: '0.001',
        value: numberValue(l.qtyAvailableToReplace),
      })),
      { key: 'dueAt', label: 'Hạn báo lại khách / xử lý', type: 'datetime-local' },
      ...fields,
    ];
  if (action === 'receipt')
    fields = [
      ...caseLines.map((l) => ({
        key: `qty_${l.id}`,
        label: `${productName(l.skuId)} (${l.uom})`,
        type: 'number' as const,
        min: 0,
        step: '0.001',
        value: numberValue(l.qty),
      })),
      ...fields,
    ];
  if (action === 'feedback')
    fields = [
      { key: 'note', label: 'Nội dung đã trao đổi và kết quả với khách', type: 'textarea' },
    ];
  async function perform(values: RecordData) {
    if (action === 'create')
      await command('/sales-replacements', {
        orderId: Number(orderId),
        quantities: Object.fromEntries(
          sourceLines
            .map((l) => [l.id, Number(values[`qty_${l.id}`])])
            .filter(([, qty]) => Number(qty) > 0),
        ),
        dueAt: new Date(textValue(values.dueAt)).toISOString(),
        reason: values.reason,
      });
    else if (action === 'feedback')
      await command(`/sales-replacements/${selected}/customer-feedback`, {
        note: values.note,
        expectedStatus: item.status,
      });
    else
      await command(`/sales-replacements/${selected}/${action}`, {
        reason: values.reason,
        ...(action === 'receipt'
          ? {
              quantities: Object.fromEntries(
                caseLines.map((l) => [l.id, Number(values[`qty_${l.id}`])]),
              ),
            }
          : {}),
      });
    setAction('');
    cases.reload();
    detail.reload();
    source.reload();
    notify('Đã lưu kết quả trên hệ thống.');
  }
  return (
    <>
      <PageHead title="Giao thiếu & phản hồi khách" />
      <LoadState {...staff} retry={staff.reload} />
      <div className="npp-queue-grid">
        <div className="npp-queue tone-attention">
          <Truck />
          <span>
            Đề xuất chờ duyệt<strong>{cases.loading ? '—' : proposed}</strong>
          </span>
        </div>
        <div className="npp-queue tone-care">
          <MessageSquare />
          <span>
            Chưa có kết quả báo khách<strong>{cases.loading ? '—' : feedback}</strong>
          </span>
        </div>
        <Link className="npp-queue npp-queue-link tone-stock" to="/demand">
          <span>
            Nhập hàng<strong>Kế hoạch nhập</strong>
          </span>
          <ArrowRight />
        </Link>
      </div>
      <LoadState {...sales} retry={sales.reload} />
      <LoadState {...cases} retry={cases.reload} />
      {role !== 'nsx' && (has('SALES_APPROVE') || has('SALES_CREATE')) && (
        <Panel title="Xử lý đơn có giao thiếu">
          <div className="toolbar">
            <label>
              Đơn cần xử lý
              <select value={orderId} onChange={(e) => setOrderId(e.target.value)}>
                <option value="">Chọn đơn đã ghi nhận giao thiếu</option>
                {sales.data
                  .filter((o) => o.status === 'DELIVERED_SHORT')
                  .map((o) => (
                    <option key={textValue(o.id)} value={textValue(o.id)}>
                      {textValue(o.code)} ·{' '}
                      {db.stores.find((s) => s.id === String(o.storeId))?.name ||
                        `Điểm bán #${o.storeId}`}
                    </option>
                  ))}
              </select>
            </label>
            <button
              className="btn primary"
              disabled={!source.data.id || source.loading || !sourceLines.length}
              onClick={() => setAction('create')}
            >
              Đề xuất giao bù <ArrowRight size={16} />
            </button>
          </div>
          <LoadState {...source} retry={source.reload} />
          {Boolean(source.data.id) && (
            <Table
              rows={sourceLines}
              columns={[
                { key: 'sku', label: 'Mặt hàng', render: (l) => productName(l.skuId) },
                { key: 'unit', label: 'Đơn vị', render: (l) => textValue(l.uom) },
                {
                  key: 'received',
                  label: 'Đã nhận ban đầu',
                  render: (l) => textValue(l.qtyReceived),
                },
                {
                  key: 'missing',
                  label: 'Còn có thể đề xuất',
                  render: (l) => numberValue(l.qtyAvailableToReplace).toLocaleString('vi-VN'),
                },
              ]}
            />
          )}
        </Panel>
      )}
      <Panel title="Chứng từ giao bù">
        <Table
          rows={cases.data}
          onRow={(r) => setSelected(textValue(r.id))}
          columns={[
            {
              key: 'id',
              label: 'Chứng từ',
              render: (r) => <strong>GB-{textValue(r.id)}</strong>,
            },
            {
              key: 'order',
              label: 'Đơn gốc',
              render: (r) =>
                (sales.data.find((o) => o.id === r.orderId)?.code as string) || `#${r.orderId}`,
            },
            {
              key: 'store',
              label: 'Điểm bán',
              render: (r) =>
                db.stores.find((s) => s.id === String(r.storeId))?.name || `#${r.storeId}`,
            },
            {
              key: 'status',
              label: 'Tiến độ hàng',
              render: (r) => <Badge status={textValue(r.status)} />,
            },
            {
              key: 'dueAt',
              label: 'Hạn xử lý',
              render: (r) => new Date(textValue(r.dueAt)).toLocaleString('vi-VN'),
            },
            {
              key: 'feedback',
              label: 'Báo lại khách',
              render: (r) => <Badge status={r.notifiedAt ? 'Đã phản hồi' : 'Chờ phản hồi'} />,
            },
          ]}
        />
      </Panel>
      {selected && (
        <Panel
          title={`Chi tiết giao bù GB-${selected}`}
          action={
            <button className="btn" onClick={() => setSelected('')}>
              Đóng chi tiết
            </button>
          }
        >
          <LoadState {...detail} retry={detail.reload} />
          {Boolean(item.id) && (
            <>
              <div className="toolbar">
                <Badge status={textValue(item.status)} />
                <span>Đề xuất bởi {person(item.createdBy)}</span>
                <span>Khoản thu thêm: {money(numberValue(item.additionalCharge))}</span>
                {role !== 'nsx' &&
                  has('SALES_APPROVE') &&
                  item.status === 'PROPOSED' &&
                  Number(item.createdBy) !== user?.id && (
                    <button className="btn primary" onClick={() => setAction('approve')}>
                      {labels.approve}
                    </button>
                  )}
                {role !== 'nsx' && has('WAREHOUSE_WRITE') && item.status === 'APPROVED' && (
                  <button className="btn primary" onClick={() => setAction('ship')}>
                    {labels.ship}
                  </button>
                )}
                {role !== 'nsx' && has('SALES_APPROVE') && item.status === 'SHIPPED' && (
                  <button className="btn primary" onClick={() => setAction('receipt')}>
                    {labels.receipt}
                  </button>
                )}
                {role !== 'nsx' &&
                  has('SALES_APPROVE') &&
                  ['PROPOSED', 'APPROVED'].includes(textValue(item.status)) && (
                    <button className="btn" onClick={() => setAction('cancel')}>
                      {labels.cancel}
                    </button>
                  )}
              </div>
              {item.status === 'PROPOSED' && Number(item.createdBy) === user?.id && (
                <p className="info-box">
                  Chờ người có quyền duyệt đơn tại NPP xác nhận. Người đề xuất không tự duyệt.
                </p>
              )}
              <Table
                rows={caseLines}
                columns={[
                  { key: 'sku', label: 'Mặt hàng', render: (l) => productName(l.skuId) },
                  { key: 'unit', label: 'Đơn vị', render: (l) => textValue(l.uom) },
                  { key: 'qty', label: 'Giao bù', render: (l) => textValue(l.qty) },
                  {
                    key: 'qtyReceived',
                    label: 'Thực nhận',
                    render: (l) => textValue(l.qtyReceived),
                  },
                ]}
              />
              <div className="npp-case-columns">
                <div className="npp-case-log">
                  <h3>Phản hồi khách hàng</h3>
                  <p>{textValue(item.reason)}</p>
                  <p>
                    Sales {person(item.repId)} · Hạn{' '}
                    {new Date(textValue(item.dueAt)).toLocaleString('vi-VN')}
                  </p>
                  <p>
                    {item.notifiedAt
                      ? textValue(item.customerFeedback)
                      : 'Chưa có kết quả báo lại khách.'}
                  </p>
                  {!item.notifiedAt && (user?.id === Number(item.repId) || !user?.nppId) && (
                    <button className="btn" onClick={() => setAction('feedback')}>
                      {labels.feedback}
                    </button>
                  )}
                </div>
                <div className="npp-case-log">
                  <h3>Lịch sử xử lý</h3>
                  {records(item.history).map((h, i) => (
                    <p key={i}>
                      <span>
                        {new Date(textValue(h.at)).toLocaleString('vi-VN')} · {person(h.actorId)} ·{' '}
                        {textValue((h.data as RecordData)?.note)}
                      </span>
                    </p>
                  ))}
                </div>
              </div>
            </>
          )}
        </Panel>
      )}
      {action && (
        <ActionForm
          title={labels[action]}
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={perform}
        />
      )}
    </>
  );
}
