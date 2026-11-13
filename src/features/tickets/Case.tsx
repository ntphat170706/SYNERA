import { useState } from 'react';
import { useStore } from '../../shared/store';
import { command, download, records, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState } from '../../shared/ApiUI';
import { Panel, Table } from '../../shared/ui';
export default function TicketCase({ id }: { id: string }) {
  const { has, user, notify } = useStore();
  const detail = useApi<RecordData>(`/tickets/${id}`, {}),
    staff = useApi<RecordData[]>(
      detail.data.nppId ? `/staff?nppId=${detail.data.nppId}` : null,
      [],
    );
  const [action, setAction] = useState('');
  return (
    <>
      <Panel
        title={`Trao đổi ticket #${id}`}
        action={
          has('TICKET_WRITE') ? (
            <span className="toolbar">
              <button className="btn" onClick={() => setAction('comment')}>
                Thêm trao đổi
              </button>
              {user?.role !== 'SALES' && (
                <button className="btn" onClick={() => setAction('assign')}>
                  Phân công
                </button>
              )}
            </span>
          ) : undefined
        }
      >
        <LoadState {...detail} retry={detail.reload} />
        <p>{textValue(detail.data.description)}</p>
        <Table
          rows={records(detail.data.comments)}
          columns={[
            { key: 'created_at', label: 'Lúc', render: (r) => textValue(r.created_at) },
            { key: 'user_id', label: 'Người gửi', render: (r) => textValue(r.user_id) },
            { key: 'body', label: 'Nội dung', render: (r) => textValue(r.body) },
            {
              key: 'attachment_id',
              label: 'Chứng từ',
              render: (r) =>
                r.attachment_id ? (
                  <button
                    className="btn"
                    onClick={() =>
                      void download(
                        `/attachments/${r.attachment_id}/content`,
                        `ticket-${id}-attachment`,
                      ).catch((e) => notify(e.message))
                    }
                  >
                    Tải tệp
                  </button>
                ) : null,
            },
          ]}
        />
      </Panel>
      {action && (
        <ActionForm
          title={action === 'comment' ? 'Trao đổi yêu cầu' : 'Phân công xử lý'}
          fields={
            action === 'comment'
              ? [
                  { key: 'body', label: 'Nội dung trao đổi', type: 'textarea' },
                  {
                    key: 'attachmentId',
                    label: 'Tệp bổ sung (không bắt buộc)',
                    type: 'attachment',
                    nppId: Number(detail.data.nppId),
                    required: false,
                  },
                ]
              : [
                  {
                    key: 'userId',
                    label: 'Nhân viên NPP xử lý',
                    options: staff.data.map((s) => ({
                      value: textValue(s.id),
                      label: `${s.name} · ${s.role}`,
                    })),
                  },
                  { key: 'reason', label: 'Lý do phân công', type: 'textarea' },
                ]
          }
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            await command(
              `/tickets/${id}/${action === 'comment' ? 'comments' : 'assign'}`,
              action === 'comment'
                ? { body: values.body, attachmentId: Number(values.attachmentId) || null }
                : { userId: Number(values.userId), reason: values.reason },
            );
            detail.reload();
            notify('Đã ghi nhận trao đổi.');
          }}
        />
      )}
    </>
  );
}
