import { useState } from 'react';
import { useStore } from '../../shared/store';
import { command, textValue, records, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { PageHead, Panel, Table, Badge } from '../../shared/ui';
import { today } from '../../shared/utils';
export default function Demand() {
  const { db, user, has, notify } = useStore();
  const plans = useApi<RecordData[]>('/demand/plans', []);
  const [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const [suggestionNpp, setSuggestionNpp] = useState(''),
    [coverDays, setCoverDays] = useState(14);
  const suggestionId = suggestionNpp || String(user?.nppId || db.partners[0]?.id || '');
  const suggestions = useApi<RecordData[]>(
    suggestionId
      ? `/demand/replenishment?nppId=${suggestionId}&coverDays=${coverDays}&lookbackDays=30`
      : null,
    [],
  );
  const fields: FormField[] =
    action === 'create'
      ? [
          {
            key: 'nppId',
            label: 'Nhà phân phối',
            value: user?.nppId,
            options: db.partners
              .filter((p) => !user?.nppId || p.id === String(user.nppId))
              .map((p) => ({ value: p.id, label: p.name })),
          },
          { key: 'month', label: 'Tháng kế hoạch', type: 'date', value: today() },
          { key: 'lines', label: 'Sản phẩm dự kiến', type: 'lines' },
        ]
      : [
          {
            key: 'status',
            label: 'Chuyển đến',
            options: (selected.status === 'DRAFT' ? ['SUBMITTED'] : ['ACCEPTED', 'ADJUSTED']).map(
              (value) => ({ value, label: value }),
            ),
          },
          { key: 'reason', label: 'Lý do / phản hồi', type: 'textarea' },
        ];
  return (
    <>
      <PageHead title="Kế hoạch nhập hàng">
        {has('SELLIN_CREATE') && (
          <button className="btn primary" onClick={() => setAction('create')}>
            Lập kế hoạch
          </button>
        )}
      </PageHead>
      <Panel title="Gợi ý nhập theo số ngày phủ">
        <div className="toolbar">
          <label>
            Nhà phân phối{' '}
            <select value={suggestionId} onChange={(e) => setSuggestionNpp(e.target.value)}>
              {db.partners.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Số ngày phủ{' '}
            <input
              type="number"
              min={1}
              max={180}
              value={coverDays}
              onChange={(e) => setCoverDays(Math.min(180, Math.max(1, Number(e.target.value))))}
            />
          </label>
        </div>
        <LoadState {...suggestions} retry={suggestions.reload} />
        <Table
          rows={suggestions.data}
          columns={[
            { key: 'name', label: 'Sản phẩm' },
            { key: 'unit', label: 'Đơn vị cơ sở' },
            { key: 'dailyBase', label: 'Bán/ngày' },
            { key: 'availableBase', label: 'Khả dụng' },
            { key: 'inboundBase', label: 'Đang về' },
            { key: 'suggestedBase', label: 'Đề xuất nhập' },
          ].map((c) => ({ ...c, render: (r: RecordData) => textValue(r[c.key]) }))}
        />
      </Panel>
      <LoadState {...plans} retry={plans.reload} />
      <Panel>
        <Table
          rows={plans.data}
          columns={[
            { key: 'id', label: 'Mã' },
            { key: 'nppId', label: 'NPP' },
            { key: 'month', label: 'Tháng' },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r: RecordData) => <Badge status={textValue(r.status)} />,
            },
            {
              key: 'lines',
              label: 'Chi tiết',
              render: (r: RecordData) =>
                records(r.lines)
                  .map((l) => `${l.skuId}: ${l.qty} ${l.uom}`)
                  .join(' · '),
            },
            {
              key: 'action',
              label: 'Xử lý',
              render: (r: RecordData) =>
                (r.status === 'DRAFT' && has('SELLIN_CREATE')) ||
                (r.status === 'SUBMITTED' && has('ORDER_APPROVE')) ? (
                  <button
                    className="btn"
                    onClick={() => {
                      setSelected(r);
                      setAction('state');
                    }}
                  >
                    Cập nhật
                  </button>
                ) : null,
            },
          ].map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
        />
      </Panel>
      {action && (
        <ActionForm
          title={action === 'create' ? 'Kế hoạch nhập hàng' : 'Xử lý kế hoạch'}
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            await command(
              action === 'create' ? '/demand/plans' : `/demand/plans/${selected.id}/transition`,
              action === 'create'
                ? {
                    nppId: Number(values.nppId),
                    month: values.month,
                    lines: JSON.parse(values.lines),
                  }
                : values,
            );
            plans.reload();
            notify('Đã lưu kế hoạch.');
          }}
        />
      )}
    </>
  );
}
