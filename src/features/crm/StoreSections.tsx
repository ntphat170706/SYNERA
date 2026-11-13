import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { records, textValue, numberValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { LoadState } from '../../shared/ApiUI';
import { Table, Badge } from '../../shared/ui';
import { money } from '../../shared/utils';

const sections = {
  orders: {
    title: 'Đơn hàng',
    columns: [
      ['code', 'Đơn'],
      ['status', 'Trạng thái'],
      ['total', 'Giá trị đơn'],
    ],
  },
  interactions: {
    title: 'Tương tác',
    columns: [
      ['kind', 'Loại'],
      ['note', 'Nội dung'],
      ['at', 'Thời điểm'],
    ],
  },
  appointments: {
    title: 'Lịch hẹn',
    columns: [
      ['title', 'Nội dung'],
      ['dueAt', 'Thời hạn'],
      ['status', 'Trạng thái'],
    ],
  },
  tickets: {
    title: 'Ticket',
    columns: [
      ['id', 'Mã'],
      ['title', 'Nội dung'],
      ['status', 'Trạng thái'],
    ],
  },
  display: {
    title: 'Trưng bày',
    columns: [
      ['id', 'Mã'],
      ['status', 'Trạng thái'],
      ['finalScore', 'Điểm đã duyệt'],
      ['provider', 'Nguồn xử lý'],
    ],
  },
};
type Section = keyof typeof sections;
function StoreSection({
  storeId,
  section,
  active,
  onComplete,
}: {
  storeId: string;
  section: Section;
  active: boolean;
  onComplete?: (row: RecordData) => void;
}) {
  const [page, setPage] = useState(0);
  const result = useApi<RecordData>(`/stores/${storeId}/360/${section}?page=${page}&size=10`, {});
  const navigate = useNavigate();
  const definition = sections[section];
  return (
    <section className="crm-record-section" hidden={!active} aria-label={definition.title}>
      <LoadState {...result} retry={result.reload} />
      <Table
        rows={records(result.data.content)}
        pageSize={10}
        pagination={false}
        onRow={
          section === 'orders'
            ? (row) => navigate('/orders?channel=sales&order=' + row.id)
            : undefined
        }
        columns={[
          ...definition.columns.map(([key, label]) => ({
            key,
            label,
            render: (row: RecordData) =>
              key === 'status' ? (
                <Badge status={textValue(row.status)} />
              ) : key === 'total' ? (
                money(numberValue(row.total))
              ) : ['at', 'dueAt'].includes(key) && row[key] ? (
                new Date(textValue(row[key])).toLocaleString('vi-VN')
              ) : key === 'kind' ? (
                { CALL: 'Cuộc gọi', VISIT: 'Ghé thăm', NOTE: 'Ghi chú', EMAIL: 'Email' }[
                  textValue(row[key])
                ] || textValue(row[key])
              ) : row[key] == null ? (
                '—'
              ) : (
                textValue(row[key])
              ),
          })),
          ...(section === 'appointments' && onComplete
            ? [
                {
                  key: 'action',
                  label: 'Kết quả',
                  render: (row: RecordData) =>
                    row.status === 'OPEN' ? (
                      <button className="btn" onClick={() => onComplete(row)}>
                        Ghi kết quả hẹn
                      </button>
                    ) : null,
                },
              ]
            : []),
        ]}
      />
      {(page > 0 || result.data.hasNext === true) && (
        <div className="crm-server-pager">
          <button
            className="btn"
            disabled={page === 0 || result.loading}
            onClick={() => setPage((p) => p - 1)}
            aria-label={`Trang trước · ${definition.title}`}
          >
            Trước
          </button>
          <button
            className="btn"
            disabled={!result.data.hasNext || result.loading}
            onClick={() => setPage((p) => p + 1)}
            aria-label={`Trang sau · ${definition.title}`}
          >
            Sau
          </button>
        </div>
      )}
    </section>
  );
}
export default function StoreSections({
  storeId,
  onComplete,
}: {
  storeId: string;
  onComplete?: (row: RecordData) => void;
}) {
  const [active, setActive] = useState<Section>('orders');
  // Each section owns its query and pager; React starts the independent reads together.
  return (
    <div className="crm-records">
      <div className="tabs crm-section-tabs" aria-label="Hồ sơ điểm bán">
        {(Object.keys(sections) as Section[]).map((section) => (
          <button
            key={section}
            className={active === section ? 'active' : ''}
            onClick={() => setActive(section)}
          >
            {sections[section].title}
          </button>
        ))}
      </div>
      {(Object.keys(sections) as Section[]).map((section) => (
        <StoreSection
          key={`${storeId}:${section}`}
          storeId={storeId}
          section={section}
          active={active === section}
          onComplete={onComplete}
        />
      ))}
    </div>
  );
}
