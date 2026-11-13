import { useState } from 'react';
import { useStore } from '../../shared/store';
import { command, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { PageHead, Panel, Table, Badge } from '../../shared/ui';
import { today } from '../../shared/utils';
export default function Engagement() {
  const { db, user, has, notify } = useStore();
  const [tab, setTab] = useState('quotations'),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const data = useApi<RecordData[]>(`/${tab}`, []);
  const stores = useApi<RecordData[]>('/stores', []);
  const fields: FormField[] =
    action === 'quotation'
      ? [
          {
            key: 'storeId',
            label: 'Cửa hàng',
            options: stores.data.map((s) => ({ value: textValue(s.id), label: textValue(s.name) })),
          },
          { key: 'validUntil', label: 'Hạn hiệu lực', type: 'date', value: today() },
          { key: 'lines', label: 'Sản phẩm báo giá', type: 'lines' },
        ]
      : action === 'campaign'
        ? [
            {
              key: 'nppId',
              label: 'NPP',
              value: user?.nppId,
              options: db.partners.map((p) => ({ value: p.id, label: p.name })),
            },
            { key: 'name', label: 'Tên chiến dịch' },
            { key: 'groupName', label: 'Nhóm cửa hàng (trống = tất cả)', required: false },
            { key: 'message', label: 'Nội dung chăm sóc', type: 'textarea' },
          ]
        : action === 'launch'
          ? [{ key: 'dueAt', label: 'Hạn hoàn thành', type: 'datetime-local' }]
          : [
              {
                key: 'status',
                label: 'Trạng thái',
                options: (selected.status === 'DRAFT'
                  ? ['SENT', 'CANCELLED']
                  : ['ACCEPTED', 'REJECTED', 'CANCELLED']
                ).map((value) => ({ value, label: value })),
              },
              { key: 'reason', label: 'Lý do', type: 'textarea' },
            ];
  return (
    <>
      <PageHead title="Báo giá & chiến dịch">
        {has('SALES_CREATE') && tab === 'quotations' && (
          <button className="btn primary" onClick={() => setAction('quotation')}>
            Tạo báo giá
          </button>
        )}
        {has('ROUTE_WRITE') && tab === 'campaigns' && (
          <button className="btn primary" onClick={() => setAction('campaign')}>
            Tạo chiến dịch
          </button>
        )}
      </PageHead>
      <div className="tabs">
        {[
          ['quotations', 'Báo giá'],
          ['campaigns', 'Chiến dịch chăm sóc'],
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
      <LoadState {...data} retry={data.reload} />
      <Panel>
        <Table
          rows={data.data}
          columns={[
            { key: 'id', label: 'Mã' },
            {
              key: tab === 'quotations' ? 'store_id' : 'name',
              label: tab === 'quotations' ? 'Cửa hàng' : 'Chiến dịch',
            },
            {
              key: tab === 'quotations' ? 'valid_until' : 'groupName',
              label: tab === 'quotations' ? 'Hiệu lực' : 'Nhóm',
            },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r: RecordData) => <Badge status={textValue(r.status)} />,
            },
            {
              key: 'action',
              label: 'Thao tác',
              render: (r: RecordData) =>
                tab === 'quotations' &&
                ['DRAFT', 'SENT'].includes(textValue(r.status)) &&
                has('SALES_CREATE') ? (
                  <button
                    className="btn"
                    onClick={() => {
                      setSelected(r);
                      setAction('quote-state');
                    }}
                  >
                    Xử lý báo giá
                  </button>
                ) : tab === 'campaigns' && r.status === 'DRAFT' && has('ROUTE_WRITE') ? (
                  <button
                    className="btn"
                    onClick={() => {
                      setSelected(r);
                      setAction('launch');
                    }}
                  >
                    Phát hành nhiệm vụ
                  </button>
                ) : null,
            },
          ].map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
        />
      </Panel>
      {action && (
        <ActionForm
          title="Báo giá / chiến dịch"
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            const path =
              action === 'quotation'
                ? '/quotations'
                : action === 'campaign'
                  ? '/campaigns'
                  : action === 'launch'
                    ? `/campaigns/${selected.id}/launch`
                    : `/quotations/${selected.id}/transition`;
            await command(
              path,
              action === 'quotation'
                ? {
                    storeId: Number(values.storeId),
                    validUntil: values.validUntil,
                    lines: JSON.parse(values.lines),
                  }
                : action === 'campaign'
                  ? { ...values, nppId: Number(values.nppId) }
                  : action === 'launch'
                    ? { dueAt: new Date(values.dueAt).toISOString() }
                    : values,
            );
            data.reload();
            notify('Đã ghi nhận trên máy chủ.');
          }}
        />
      )}
    </>
  );
}
