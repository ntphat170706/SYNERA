import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Plus, RefreshCw, ArrowLeft, ArrowRight } from 'lucide-react';
import { useStore } from '../../shared/store';
import { command, records, textValue, numberValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { Badge, PageHead, Panel, SearchBox, Table } from '../../shared/ui';
import { money } from '../../shared/utils';
import { orderInQueue } from '../../shared/nppOperations';
import FulfillmentSummary from './FulfillmentSummary';
import { unitName, productImage } from '../../shared/productMedia';
const label: Record<string, string> = {
  submit: 'Gửi duyệt',
  approve: 'Duyệt đơn',
  reject: 'Từ chối',
  'need-info': 'Yêu cầu bổ sung',
  resubmit: 'Gửi lại',
  confirm: 'Xác nhận số lượng',
  ship: 'Xuất kho',
  dispatch: 'Bắt đầu giao',
  delivered: 'Xác nhận đã giao',
  receipt: 'Ghi nhận thực nhận',
  reconcile: 'Đối soát',
  close: 'Đóng đơn',
  cancel: 'Hủy đơn',
  reorder: 'Đặt lại',
  return: 'Tạo phiếu hoàn',
};
export default function Orders() {
  const { role, db, has, products, notify, refresh } = useStore();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const channel = role === 'sales' ? 'sales' : params.get('channel') || 'sellin';
  const base = channel === 'sales' ? '/sales-orders' : '/sell-in-orders';
  const [query, setQuery] = useState(''),
    [action, setAction] = useState('');
  const list = useApi<RecordData[]>(base, []),
    detail = useApi<RecordData>(params.get('order') ? `${base}/${params.get('order')}` : null, {});
  const status = textValue(detail.data.status),
    lines = records(detail.data.lines);
  const choose = (id: string) =>
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set('channel', channel);
      next.set('order', id);
      return next;
    });
  const backToList = () =>
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      next.delete('order');
      next.set('channel', channel);
      return next;
    });
  const queue = params.get('status') || 'all';
  const canManage =
    has(channel === 'sales' ? 'SALES_APPROVE' : 'ORDER_APPROVE') &&
    !(role === 'nsx' && channel === 'sales');
  const canCreate = has(channel === 'sales' ? 'SALES_CREATE' : 'SELLIN_CREATE');
  let actions: string[] = [];
  if (channel === 'sellin') {
    if (status === 'DRAFT' && canCreate) actions.push('submit');
    if (['PENDING_APPROVAL', 'ON_HOLD_CREDIT'].includes(status) && canManage)
      actions.push('approve', 'need-info', 'reject');
    if (status === 'NEED_INFO' && canCreate) actions.push('resubmit');
    if (status === 'APPROVED' && canManage) actions.push('confirm');
    if (status === 'CONFIRMED_QTY' && canManage) actions.push('ship');
    if (status === 'SHIPPED' && canManage) actions.push('dispatch');
    if (status === 'IN_DELIVERY' && canManage) actions.push('delivered');
    if (status === 'DELIVERED' && (canCreate || has('WAREHOUSE_WRITE') || canManage))
      actions.push('receipt');
    if (
      ['RECEIVED', 'RECEIVED_DISCREPANCY', 'AUTO_RECEIVED'].includes(status) &&
      has('CLOSING_WRITE')
    )
      actions.push('reconcile');
    if (status === 'RECONCILED' && has('CLOSING_WRITE')) actions.push('close');
    if (['RECEIVED', 'AUTO_RECEIVED', 'RECONCILED', 'CLOSED'].includes(status) && canCreate)
      actions.push('return');
    if (canCreate) actions.push('reorder');
    if (
      [
        'DRAFT',
        'PENDING_APPROVAL',
        'ON_HOLD_CREDIT',
        'NEED_INFO',
        'APPROVED',
        'CONFIRMED_QTY',
      ].includes(status) &&
      (canCreate || canManage)
    )
      actions.push('cancel');
  } else if (canManage) {
    if (status === 'SUBMITTED') actions.push('confirm', 'cancel');
    if (status === 'NPP_CONFIRMED') actions.push('ship', 'cancel');
    if (status === 'PICKING') actions.push('dispatch');
    if (status === 'DELIVERING') actions.push('receipt');
  }
  const primaryAction = actions.find((value) => !['cancel', 'reorder', 'return'].includes(value));
  const fields: FormField[] = [{ key: 'reason', label: 'Lý do / ghi chú', type: 'textarea' }];
  if (['confirm', 'receipt', 'return'].includes(action))
    for (const line of lines) {
      const name =
        products.find((p) => Number(p.id) === numberValue(line.skuId))?.name || `SKU ${line.skuId}`;
      fields.unshift({
        key: `qty_${line.id}`,
        label: `${name} (${unitName(textValue(line.uom))})`,
        type: 'number',
        value: numberValue(
          line[
            action === 'confirm' ? 'qtyOrdered' : action === 'return' ? 'qtyReceived' : 'qtyShipped'
          ],
        ),
        min: 0,
        step: '0.001',
      });
      if (action === 'receipt' && channel === 'sellin')
        fields.push(
          {
            key: `damaged_${line.id}`,
            label: `${name} - hỏng`,
            type: 'number',
            value: 0,
            min: 0,
            step: '0.001',
          },
          {
            key: `returned_${line.id}`,
            label: `${name} - hoàn tại nhận`,
            type: 'number',
            value: 0,
            min: 0,
            step: '0.001',
          },
        );
    }
  async function perform(values: Record<string, string>) {
    let body: RecordData = { reason: values.reason };
    const quantities = Object.fromEntries(
      lines.map((l) => [textValue(l.id), Number(values[`qty_${l.id}`])]),
    );
    if (action === 'confirm' || (action === 'receipt' && channel === 'sales'))
      body = { ...body, quantities };
    if (action === 'receipt' && channel === 'sellin')
      body = {
        ...body,
        lines: lines.map((l) => ({
          lineId: l.id,
          received: Number(values[`qty_${l.id}`]),
          damaged: Number(values[`damaged_${l.id}`] || 0),
          returned: Number(values[`returned_${l.id}`] || 0),
        })),
      };
    const result =
      action === 'return'
        ? await command('/returns', {
            orderId: detail.data.id,
            quantities: Object.fromEntries(Object.entries(quantities).filter(([, qty]) => qty > 0)),
            reason: values.reason,
          })
        : await command(`${base}/${detail.data.id}/${action}`, body);
    notify(
      action === 'return' ? 'Đã tạo phiếu hoàn, chờ NSX duyệt.' : `${label[action]} thành công.`,
    );
    list.reload();
    detail.reload();
    await refresh();
    if (action === 'reorder') choose(textValue(result.id));
  }
  return (
    <>
      <PageHead
        title={
          params.get('order')
            ? `Chi tiết ${textValue(detail.data.code) || params.get('order')}`
            : 'Đơn hàng'
        }
      >
        {params.get('order') && (
          <button className="text-button" onClick={backToList}>
            <ArrowLeft size={16} />
            Danh sách đơn
          </button>
        )}
        {role !== 'npp' && (
          <button className="btn" onClick={list.reload}>
            <RefreshCw size={16} />
            Làm mới
          </button>
        )}
        {canCreate && (
          <button
            className="btn primary"
            onClick={() => navigate(channel === 'sales' ? '/catalog?channel=sales' : '/catalog')}
          >
            <Plus size={16} />
            Tạo đơn hàng
          </button>
        )}
      </PageHead>
      {!params.get('order') && (
        <>
          {role === 'npp' && (
            <div className="tabs">
              {[
                ['sellin', 'Đơn nhập từ NSX'],
                ['sales', 'Đơn bán cửa hàng'],
              ].map(([value, title]) => (
                <button
                  key={value}
                  className={channel === value ? 'active' : ''}
                  onClick={() => setParams({ channel: value })}
                >
                  {title}
                </button>
              ))}
            </div>
          )}
          <LoadState {...list} retry={list.reload} />
          <div className="tabs npp-order-filters">
            {[
              ['all', 'Tất cả'],
              ['pending', 'Chờ xử lý'],
              ...(role === 'npp'
                ? [
                    ...(channel === 'sellin' ? [['receiving', 'Cần nhận hàng']] : []),
                    ['short', 'Có chênh lệch'],
                  ]
                : []),
            ].map(([value, title]) => (
              <button
                key={value}
                className={queue === value ? 'active' : ''}
                onClick={() => setParams({ channel, status: value })}
              >
                {title}
                <span className="npp-tab-count">
                  {list.data.filter((o) => orderInQueue(o, value, channel, role)).length}
                </span>
              </button>
            ))}
          </div>
          <Panel className="order-list-panel">
            <div className="table-toolbar">
              <SearchBox value={query} onChange={setQuery} />
            </div>
            <Table
              rows={list.data
                .filter((o) => orderInQueue(o, queue, channel, role))
                .filter((o) => JSON.stringify(o).toLowerCase().includes(query.toLowerCase()))}
              searchText={query}
              onRow={(row) => choose(textValue(row.id))}
              columns={[
                {
                  key: 'code',
                  label: 'Mã đơn',
                  render: (o) => <strong>{textValue(o.code)}</strong>,
                },
                {
                  key: 'npp',
                  label: channel === 'sales' ? 'Cửa hàng' : 'Nhà phân phối',
                  render: (o) =>
                    channel === 'sales'
                      ? db.stores.find((s) => s.id === String(o.storeId))?.name ||
                        `Điểm bán #${o.storeId}`
                      : db.partners.find((p) => p.id === String(o.nppId))?.name ||
                        `NPP #${o.nppId}`,
                },
                {
                  key: 'date',
                  label: 'Ngày tạo',
                  render: (o) => new Date(textValue(o.createdAt)).toLocaleDateString('vi-VN'),
                },
                {
                  key: 'status',
                  label: 'Trạng thái',
                  render: (o) => <Badge status={textValue(o.status)} />,
                },
                { key: 'total', label: 'Giá trị đơn', render: (o) => money(numberValue(o.total)) },
                {
                  key: 'action',
                  label: 'Thao tác',
                  render: (o) => (
                    <button
                      className="btn order-process-button"
                      onClick={() => choose(textValue(o.id))}
                    >
                      Xử lý đơn
                      <ArrowRight size={15} />
                    </button>
                  ),
                },
              ]}
            />
          </Panel>
        </>
      )}
      {params.get('order') && (
        <Panel
          title={
            channel === 'sales'
              ? db.stores.find((s) => s.id === String(detail.data.storeId))?.name || 'Đơn điểm bán'
              : 'Đơn nhập từ nhà sản xuất'
          }
          className="npp-detail-surface"
          action={
            <button className="btn" onClick={backToList}>
              Đóng chi tiết
            </button>
          }
        >
          <LoadState {...detail} retry={detail.reload} />
          {Boolean(detail.data.id) && (
            <>
              <OrderJourney status={status} channel={channel} />
              <div className="order-next-action">
                <strong>
                  {primaryAction
                    ? `Tiếp theo: ${label[primaryAction]}`
                    : channel === 'sales'
                      ? 'Theo dõi giao nhận và chăm sóc'
                      : 'Theo dõi xử lý từ nhà sản xuất'}
                </strong>
                {primaryAction && (
                  <button className="btn primary" onClick={() => setAction(primaryAction)}>
                    {label[primaryAction]}
                    <ArrowRight size={16} />
                  </button>
                )}
              </div>
              <div className="toolbar">
                {channel === 'sales' && status === 'DELIVERED_SHORT' ? (
                  <span className="order-history-status">Lịch sử: Giao thiếu</span>
                ) : (
                  <Badge status={status} />
                )}
                <strong>{money(numberValue(detail.data.total))}</strong>
                {channel === 'sales' && status === 'DELIVERED_SHORT' && (
                  <button
                    className="btn"
                    onClick={() => navigate(`/npp-workflow?order=${detail.data.id}`)}
                  >
                    Theo phần giao thiếu
                  </button>
                )}
                {actions
                  .filter((value) => value !== primaryAction)
                  .map((value) => (
                    <button className="btn" key={value} onClick={() => setAction(value)}>
                      {label[value]}
                    </button>
                  ))}
              </div>
              {channel === 'sales' && lines.some((line) => numberValue(line.qtyShipped) > 0) && (
                <FulfillmentSummary key={textValue(detail.data.id)} order={detail.data} />
              )}
              <Table
                rows={lines}
                pageSize={100}
                columns={[
                  {
                    key: 'sku',
                    label: 'Sản phẩm',
                    render: (l) => {
                      const product = products.find((p) => Number(p.id) === numberValue(l.skuId));
                      const name = product?.name || textValue(l.skuId);
                      return role === 'nsx' ? (
                        <span className="nsx-order-product">
                          {productImage(name) && <img src={productImage(name)} alt="" />}
                          <span>
                            <strong>{name}</strong>
                            <small>{`SKU ${l.skuId}`}</small>
                          </span>
                        </span>
                      ) : (
                        name
                      );
                    },
                  },
                  { key: 'uom', label: 'Đơn vị', render: (l) => unitName(textValue(l.uom)) },
                  ...['qtyOrdered', 'qtyConfirmed', 'qtyShipped', 'qtyReceived'].map((key, i) => ({
                    key,
                    label: ['Đặt', 'Xác nhận', 'Thực xuất', 'Thực nhận'][i],
                    render: (l: RecordData) => textValue(l[key]),
                  })),
                  ...(channel === 'sales'
                    ? [
                        {
                          key: 'qtyReplacementReceived',
                          label: 'Nhận giao bù',
                          render: (l: RecordData) => textValue(l.qtyReplacementReceived ?? 0),
                        },
                      ]
                    : []),
                  { key: 'price', label: 'Giá chốt', render: (l) => money(numberValue(l.price)) },
                  {
                    key: 'discount',
                    label: 'Ưu đãi',
                    render: (l) => money(numberValue(l.discount)),
                  },
                  ...(role === 'nsx'
                    ? [
                        {
                          key: 'lineTotal',
                          label: 'Thành tiền',
                          render: (l: RecordData) => money(numberValue(l.lineTotal)),
                        },
                      ]
                    : []),
                ]}
              />
              {role === 'nsx' && records(detail.data.benefits).length > 0 && (
                <section className="nsx-order-benefits">
                  <h3>Chương trình đã áp dụng</h3>
                  <Table
                    rows={records(detail.data.benefits)}
                    columns={[
                      { key: 'name', label: 'Chương trình', render: (b) => textValue(b.name) },
                      {
                        key: 'amount',
                        label: 'Giá trị ưu đãi đã chốt',
                        render: (b) => money(numberValue(b.amount)),
                      },
                      {
                        key: 'released',
                        label: 'Trạng thái',
                        render: (b) => (b.released ? 'Đã giải phóng' : 'Đã áp dụng'),
                      },
                    ]}
                  />
                </section>
              )}
              <div className="timeline">
                {records(detail.data.history).map((h, i) => (
                  <div key={i}>
                    <Badge status={textValue(h.status)} />
                    <small>{new Date(textValue(h.at)).toLocaleString('vi-VN')}</small>
                    <p>{textValue(h.note)}</p>
                  </div>
                ))}
              </div>
            </>
          )}
        </Panel>
      )}
      {action && (
        <ActionForm
          title={label[action]}
          fields={fields}
          context={`${textValue(detail.data.code)} · ${channel === 'sales' ? db.stores.find((s) => s.id === String(detail.data.storeId))?.name || 'Điểm bán' : 'Nhập từ NSX'}`}
          intro={
            <div className="order-action-summary">
              <span>
                Giá trị đơn<strong>{money(numberValue(detail.data.total))}</strong>
              </span>
              <span>
                Trạng thái
                <Badge status={status} />
              </span>
            </div>
          }
          onClose={() => setAction('')}
          onSubmit={perform}
        />
      )}
    </>
  );
}

function OrderJourney({ status, channel }: { status: string; channel: string }) {
  const labels =
    channel === 'sales'
      ? ['Tiếp nhận', 'Xác nhận', 'Xuất kho', 'Giao hàng', 'Thực nhận']
      : ['Đặt nhập', 'NSX xác nhận', 'Chuẩn bị hàng', 'Giao nhận', 'Đối chiếu'];
  const stages: Record<string, number> =
    channel === 'sales'
      ? {
          SUBMITTED: 0,
          NPP_CONFIRMED: 1,
          PICKING: 2,
          DELIVERING: 3,
          DELIVERED: 4,
          DELIVERED_SHORT: 4,
        }
      : {
          DRAFT: 0,
          PENDING_APPROVAL: 1,
          ON_HOLD_CREDIT: 1,
          NEED_INFO: 1,
          APPROVED: 2,
          CONFIRMED_QTY: 2,
          SHIPPED: 3,
          IN_DELIVERY: 3,
          DELIVERED: 3,
          RECEIVED: 4,
          RECEIVED_DISCREPANCY: 4,
          AUTO_RECEIVED: 4,
          RECONCILED: 4,
          CLOSED: 4,
        };
  const current = stages[status];
  if (current === undefined) return null;
  return (
    <ol className="order-journey" aria-label="Tiến trình đơn hàng">
      {labels.map((name, i) => (
        <li
          key={name}
          className={i < current ? 'completed' : i === current ? 'current' : ''}
          aria-current={i === current ? 'step' : undefined}
        >
          <span>Bước {i + 1}</span>
          {name}
        </li>
      ))}
    </ol>
  );
}
