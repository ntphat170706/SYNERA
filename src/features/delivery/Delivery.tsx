import { useState } from 'react';
import { useStore } from '../../shared/store';
import { command, textValue, records, record, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { PageHead, Panel, Table, Badge } from '../../shared/ui';
import { today } from '../../shared/utils';
export default function Delivery() {
  const { db, user, has, notify } = useStore();
  const [npp, setNpp] = useState(user?.nppId || 0),
    [selectedId, setId] = useState(''),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const nppId = npp || Number(db.partners[0]?.id);
  const manager = has('WAREHOUSE_WRITE');
  const trips = useApi<RecordData[]>('/delivery-trips', []),
    detail = useApi<RecordData>(selectedId ? `/delivery-trips/${selectedId}` : null, {}),
    staff = useApi<RecordData[]>(manager && nppId ? `/staff?nppId=${nppId}&role=DRIVER` : null, []),
    orders = useApi<RecordData[]>(manager ? '/sales-orders' : null, []);
  const fields: FormField[] =
    action === 'create'
      ? [
          {
            key: 'driverId',
            label: 'Tài xế',
            options: staff.data
              .filter((s) => s.role === 'DRIVER')
              .map((s) => ({ value: textValue(s.id), label: textValue(s.name) })),
          },
          { key: 'deliveryDate', label: 'Ngày giao', type: 'date', value: today() },
          {
            key: 'orderIds',
            label: 'Đơn cửa hàng đã xuất kho',
            type: 'multiselect',
            options: orders.data
              .filter((o) => Number(o.nppId) === nppId && o.status === 'PICKING')
              .map((o) => ({ value: textValue(o.id), label: textValue(o.code) })),
          },
        ]
      : action === 'dispatch'
        ? [{ key: 'reason', label: 'Ghi chú xuất phát' }]
        : [
            {
              key: 'proofAttachmentId',
              label: 'Ảnh xác nhận giao',
              type: 'attachment',
              nppId: Number(detail.data.npp_id),
            },
            ...records(selected.lines).map((l) => ({
              key: `qty-${l.id}`,
              label: `SKU ${l.skuId} · thực nhận ${l.uom}`,
              type: 'number',
              min: 0,
              step: '0.001',
              value: Number(l.qtyShipped),
            })),
            { key: 'note', label: 'Ghi chú giao hàng', type: 'textarea' },
          ];
  return (
    <>
      <PageHead title="Chuyến giao hàng">
        {manager && (
          <button className="btn primary" onClick={() => setAction('create')}>
            Lập chuyến giao
          </button>
        )}
      </PageHead>
      {manager && (
        <label>
          NPP
          <select
            disabled={Boolean(user?.nppId)}
            value={nppId || ''}
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
      <LoadState {...trips} retry={trips.reload} />
      <Panel>
        <Table
          rows={trips.data}
          onRow={(r) => setId(textValue(r.id))}
          columns={[
            { key: 'id', label: 'Chuyến' },
            { key: 'npp_id', label: 'NPP' },
            { key: 'driver_id', label: 'Tài xế' },
            { key: 'delivery_date', label: 'Ngày giao' },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r: RecordData) => <Badge status={textValue(r.status)} />,
            },
          ].map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
        />
      </Panel>
      {selectedId && (
        <Panel title={`Chuyến #${selectedId}`}>
          <LoadState {...detail} retry={detail.reload} />
          {detail.data.status === 'PLANNED' && (
            <button className="btn primary" onClick={() => setAction('dispatch')}>
              Xuất phát
            </button>
          )}
          <Table
            rows={records(detail.data.stops)}
            columns={[
              { key: 'sequence_no', label: 'Điểm' },
              { key: 'order_id', label: 'Đơn' },
              {
                key: 'destination',
                label: 'Nơi giao',
                render: (r: RecordData) => textValue(record(r.destination).name),
              },
              {
                key: 'status',
                label: 'Trạng thái',
                render: (r: RecordData) => <Badge status={textValue(r.status)} />,
              },
              {
                key: 'action',
                label: 'Giao hàng',
                render: (r: RecordData) =>
                  r.status === 'DELIVERING' ? (
                    <button
                      className="btn"
                      onClick={() => {
                        setSelected(r);
                        setAction('deliver');
                      }}
                    >
                      Xác nhận giao
                    </button>
                  ) : null,
              },
            ].map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
          />
        </Panel>
      )}
      {action && (
        <ActionForm
          title="Chuyến giao"
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            const quantities = Object.fromEntries(
              records(selected.lines).map((l) => [textValue(l.id), Number(values[`qty-${l.id}`])]),
            );
            await command(
              action === 'create'
                ? '/delivery-trips'
                : action === 'dispatch'
                  ? `/delivery-trips/${selectedId}/dispatch`
                  : `/delivery-trips/${selectedId}/stops/${selected.id}/deliver`,
              action === 'create'
                ? {
                    nppId,
                    driverId: Number(values.driverId),
                    deliveryDate: values.deliveryDate,
                    stops: values.orderIds
                      .split(',')
                      .map((id) => ({ channel: 'SELL_OUT', orderId: Number(id) })),
                  }
                : action === 'deliver'
                  ? {
                      proofAttachmentId: Number(values.proofAttachmentId),
                      quantities,
                      note: values.note,
                    }
                  : {},
            );
            trips.reload();
            detail.reload();
            orders.reload();
            notify('Đã cập nhật chuyến giao.');
          }}
        />
      )}
    </>
  );
}
