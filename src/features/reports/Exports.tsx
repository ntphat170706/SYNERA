import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useStore } from '../../shared/store';
import { command, download, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState } from '../../shared/ApiUI';
import { Panel, Table, Badge } from '../../shared/ui';
import { today } from '../../shared/utils';
export default function Exports() {
  const { role, notify } = useStore();
  const [params, setParams] = useSearchParams();
  const requestedType = ['SELL_IN', 'SELL_OUT'].includes(params.get('export') || '')
    ? params.get('export')
    : null;
  const [action, setAction] = useState(requestedType ? 'export' : '');
  const exports = useApi<RecordData[]>('/reports/exports', []),
    schedules = useApi<RecordData[]>('/reports/schedules', []);
  return (
    <>
      <Panel
        title="Xuất báo cáo & lịch email"
        action={
          <div className="toolbar">
            <button className="btn primary" onClick={() => setAction('export')}>
              Xuất Excel / PDF
            </button>
            <button className="btn" onClick={() => setAction('schedule')}>
              Lịch gửi email
            </button>
          </div>
        }
      >
        {role !== 'npp' && (
          <p>
            File được tạo nền, lưu 24 giờ và chỉ tải được trong tài khoản/phạm vi đã yêu cầu. Email
            định kỳ gửi đến email đăng nhập.
          </p>
        )}
        <LoadState {...exports} retry={exports.reload} />
        <Table
          rows={exports.data}
          columns={[
            { key: 'id', label: 'Mã' },
            { key: 'report_type', label: 'Loại' },
            { key: 'format', label: 'Định dạng' },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r: RecordData) => <Badge status={textValue(r.status)} />,
            },
            { key: 'error_message', label: 'Thông tin lỗi' },
            {
              key: 'action',
              label: 'Tải file',
              render: (r: RecordData) =>
                r.status === 'DONE' ? (
                  <button
                    className="btn"
                    onClick={() =>
                      void download(
                        `/reports/exports/${r.id}/download`,
                        `synera-${r.id}.${String(r.format).toLowerCase()}`,
                      ).catch((e) => notify(e.message))
                    }
                  >
                    Tải báo cáo
                  </button>
                ) : null,
            },
          ].map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
        />
        <Table
          rows={schedules.data}
          columns={[
            { key: 'report_type', label: 'Lịch báo cáo' },
            { key: 'cadence', label: 'Chu kỳ' },
            { key: 'email', label: 'Email nhận' },
            { key: 'next_run', label: 'Lần kế tiếp' },
            { key: 'last_error', label: 'Thông tin lỗi' },
            {
              key: 'action',
              label: 'Thao tác',
              render: (r: RecordData) =>
                r.active ? (
                  <button
                    className="btn"
                    onClick={() =>
                      void command(`/reports/schedules/${r.id}/cancel`)
                        .then(schedules.reload)
                        .catch((e) => notify(e.message))
                    }
                  >
                    Dừng lịch
                  </button>
                ) : (
                  <span>Đã dừng</span>
                ),
            },
          ].map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
        />
      </Panel>
      {action && (
        <ActionForm
          title={action === 'export' ? 'Yêu cầu xuất báo cáo' : 'Lịch báo cáo định kỳ'}
          fields={[
            {
              key: 'type',
              label: 'Loại dữ liệu',
              value: requestedType || (role === 'sales' ? 'SELL_OUT' : 'SELL_IN'),
              options: (role === 'sales' ? ['SELL_OUT'] : ['SELL_IN', 'SELL_OUT']).map((value) => ({
                value,
                label: value === 'SELL_IN' ? 'Đơn nhập NPP' : 'Đơn bán cửa hàng',
              })),
            },
            ...(action === 'export'
              ? [
                  {
                    key: 'format',
                    label: 'Định dạng',
                    options: ['XLSX', 'PDF'].map((value) => ({ value, label: value })),
                  },
                  { key: 'from', label: 'Từ ngày', type: 'date', value: today() },
                  { key: 'to', label: 'Đến ngày', type: 'date', value: today() },
                ]
              : [
                  {
                    key: 'cadence',
                    label: 'Chu kỳ',
                    options: [
                      { value: 'DAILY', label: 'Hàng ngày' },
                      { value: 'WEEKLY', label: 'Hàng tuần' },
                      { value: 'MONTHLY', label: 'Hàng tháng' },
                    ],
                  },
                ]),
          ]}
          onClose={() => {
            setAction('');
            if (requestedType) setParams({});
          }}
          onSubmit={async (values) => {
            await command(action === 'export' ? '/reports/exports' : '/reports/schedules', values);
            exports.reload();
            schedules.reload();
            notify('Đã tạo yêu cầu. Theo dõi trạng thái tại danh sách.');
          }}
        />
      )}
    </>
  );
}
