import { useState } from 'react';
import { useStore } from '../../shared/store';
import { command, download, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { Panel, Table, Badge } from '../../shared/ui';
export default function Documents() {
  const { db, user, has, notify } = useStore();
  const docs = useApi<RecordData[]>('/policy-documents', []);
  const [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({});
  const fields: FormField[] =
    action === 'create'
      ? [
          {
            key: 'nppId',
            label: 'Phạm vi',
            value: user?.nppId || 0,
            options: [
              { value: 0, label: 'Toàn hệ thống' },
              ...db.partners.map((p) => ({ value: p.id, label: p.name })),
            ],
          },
          { key: 'title', label: 'Tiêu đề' },
          { key: 'body', label: 'Nội dung chính sách', type: 'textarea' },
        ]
      : [{ key: 'reason', label: 'Lý do phát hành', type: 'textarea' }];
  return (
    <>
      <Panel
        title="Tài liệu chính sách"
        action={
          has('POLICY_WRITE') ? (
            <button className="btn" onClick={() => setAction('create')}>
              Soạn tài liệu
            </button>
          ) : undefined
        }
      >
        <LoadState {...docs} retry={docs.reload} />
        <Table
          rows={docs.data}
          columns={[
            {
              key: 'title',
              label: 'Tiêu đề',
              render: (r: RecordData) => (
                <details>
                  <summary>{textValue(r.title)}</summary>
                  <p>{textValue(r.body)}</p>
                </details>
              ),
            },
            { key: 'npp_id', label: 'NPP' },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r: RecordData) => <Badge status={textValue(r.status)} />,
            },
            {
              key: 'action',
              label: 'Xác nhận',
              render: (r: RecordData) => (
                <>
                  {r.status === 'DRAFT' && has('POLICY_WRITE') && (
                    <button
                      className="btn"
                      onClick={() => {
                        setSelected(r);
                        setAction('publish');
                      }}
                    >
                      Phát hành
                    </button>
                  )}
                  {r.status === 'PUBLISHED' && !r.acknowledged && (
                    <button
                      className="btn"
                      onClick={() =>
                        void command(`/policy-documents/${r.id}/acknowledge`)
                          .then(docs.reload)
                          .catch((e) => notify(e.message))
                      }
                    >
                      Đã đọc & xác nhận
                    </button>
                  )}
                  {Boolean(r.acknowledged) && <span>Đã xác nhận</span>}
                  {Boolean(r.attachment_id) && (
                    <button
                      className="btn"
                      onClick={() =>
                        void download(
                          `/attachments/${r.attachment_id}/content`,
                          `synera-policy-${r.id}`,
                        ).catch((e) => notify(e.message))
                      }
                    >
                      Tệp đính kèm
                    </button>
                  )}
                </>
              ),
            },
          ].map((c) => ({ ...c, render: c.render || ((r: RecordData) => textValue(r[c.key])) }))}
        />
      </Panel>
      {action && (
        <ActionForm
          title="Tài liệu chính sách"
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            await command(
              action === 'create'
                ? '/policy-documents'
                : `/policy-documents/${selected.id}/publish`,
              action === 'create' ? { ...values, nppId: Number(values.nppId) || null } : values,
            );
            docs.reload();
          }}
        />
      )}
    </>
  );
}
