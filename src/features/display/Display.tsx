import { useState, useEffect } from 'react';
import { useStore } from '../../shared/store';
import { api, command, download, textValue, numberValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { PageHead, Panel, Table, Badge } from '../../shared/ui';
function DisplayEvidence({ item }: { item: RecordData }) {
  const { user } = useStore();
  const [url, setUrl] = useState(''),
    [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    let objectUrl = '';
    setUrl('');
    setError('');
    void api<Blob>(`/attachments/${item.attachmentId}/content`, {
      raw: true,
      signal: controller.signal,
    })
      .then((blob) => {
        if (!controller.signal.aborted) {
          objectUrl = URL.createObjectURL(blob);
          setUrl(objectUrl);
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [item.attachmentId, user?.id, user?.workspaceKey]);
  return (
    <div className="display-evidence">
      <div className="display-photo">
        {url ? (
          <img src={url} alt={`Ảnh trưng bày #${item.id}`} />
        ) : (
          <p role={error ? 'alert' : 'status'}>{error || 'Đang tải ảnh...'}</p>
        )}
      </div>
      <dl>
        <div>
          <dt>Trạng thái</dt>
          <dd>
            <Badge status={textValue(item.status)} />
          </dd>
        </div>
        <div>
          <dt>Điểm xử lý</dt>
          <dd>{item.aiScore == null ? '—' : numberValue(item.aiScore)}</dd>
        </div>
        <div>
          <dt>Độ tin cậy</dt>
          <dd>
            {item.confidence == null ? '—' : `${Math.round(numberValue(item.confidence) * 100)}%`}
          </dd>
        </div>
        <div>
          <dt>Nguồn xử lý</dt>
          <dd>{textValue(item.provider) || '—'}</dd>
        </div>
        <div>
          <dt>Điểm đã duyệt</dt>
          <dd>{item.finalScore == null ? '—' : numberValue(item.finalScore)}</dd>
        </div>
      </dl>
    </div>
  );
}
export default function Display() {
  const { user, db, has, notify } = useStore();
  const [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({}),
    [storeId, setStoreId] = useState('');
  const data = useApi<RecordData[]>('/display-audits', []),
    criteria = useApi<RecordData[]>('/display-audits/criteria', []),
    stores = useApi<RecordData[]>('/stores', []);
  const store = stores.data.find((s) => String(s.id) === storeId) || stores.data[0];
  const npp = Number(store?.nppId) || user?.nppId || Number(db.partners[0]?.id);
  const fields: FormField[] =
    action === 'criteria'
      ? [
          { key: 'name', label: 'Tên bộ tiêu chí' },
          { key: 'expected', label: 'SKU kỳ vọng (mã, cách nhau bởi dấu phẩy)' },
          { key: 'minFacings', label: 'Số mặt tối thiểu', type: 'number', min: 1, value: 1 },
        ]
      : action === 'submit'
        ? [
            {
              key: 'criteriaId',
              label: 'Bộ tiêu chí',
              options: criteria.data.map((c) => ({
                value: textValue(c.id),
                label: textValue(c.name),
              })),
            },
            { key: 'attachmentId', label: 'Ảnh trưng bày', type: 'attachment', nppId: npp },
          ]
        : [
            {
              key: 'score',
              label: 'Điểm duyệt (0–100)',
              type: 'number',
              min: 0,
              max: 100,
              step: '0.01',
            },
            { key: 'reason', label: 'Nhận xét và lý do', type: 'textarea' },
          ];
  return (
    <>
      <PageHead title="Kiểm tra trưng bày">
        {has('DISPLAY_REVIEW') && (
          <button className="btn" onClick={() => setAction('criteria')}>
            Tạo bộ tiêu chí
          </button>
        )}
        {has('SALES_CREATE') && store && (
          <button className="btn primary" onClick={() => setAction('submit')}>
            Gửi ảnh kiểm tra
          </button>
        )}
      </PageHead>
      <label>
        Cửa hàng
        <select
          aria-label="Cửa hàng"
          value={textValue(store?.id)}
          onChange={(e) => setStoreId(e.target.value)}
        >
          {stores.data.map((s) => (
            <option value={textValue(s.id)} key={textValue(s.id)}>
              {textValue(s.name)}
            </option>
          ))}
        </select>
      </label>
      <LoadState {...data} retry={data.reload} />
      <Panel>
        <Table
          rows={data.data.filter((r) => !store || String(r.storeId) === String(store.id))}
          columns={[
            { key: 'id', label: 'Mã' },
            {
              key: 'storeId',
              label: 'Cửa hàng',
              render: (r: RecordData) =>
                textValue(stores.data.find((s) => s.id === r.storeId)?.name) || `#${r.storeId}`,
            },
            { key: 'provider', label: 'Nguồn xử lý' },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (r: RecordData) => <Badge status={textValue(r.status)} />,
            },
            { key: 'aiScore', label: 'Điểm xử lý' },
            { key: 'confidence', label: 'Độ tin cậy' },
            { key: 'finalScore', label: 'Điểm cuối' },
            {
              key: 'action',
              label: 'Thao tác',
              render: (r: RecordData) => (
                <div className="table-actions">
                  <button
                    className="btn"
                    onClick={() =>
                      void download(
                        `/attachments/${r.attachmentId}/content`,
                        `synera-display-${r.id}`,
                      ).catch((e) => notify(e.message))
                    }
                  >
                    Tải ảnh gốc
                  </button>
                  {r.status === 'NEEDS_REVIEW' && has('DISPLAY_REVIEW') && (
                    <button
                      className="btn"
                      onClick={() => {
                        setSelected(r);
                        setAction('review');
                      }}
                    >
                      Duyệt ảnh
                    </button>
                  )}
                </div>
              ),
            },
          ].map((c) => ({
            ...c,
            render:
              c.render || ((r: RecordData) => (r[c.key] == null ? 'Chưa có' : textValue(r[c.key]))),
          }))}
        />
      </Panel>
      {action && (
        <ActionForm
          title={
            action === 'review'
              ? `Duyệt trưng bày #${selected.id}`
              : action === 'criteria'
                ? 'Bộ tiêu chí trưng bày'
                : 'Gửi ảnh trưng bày'
          }
          wide={action === 'review'}
          context={textValue(
            stores.data.find((s) => s.id === (action === 'review' ? selected.storeId : store?.id))
              ?.name,
          )}
          intro={action === 'review' ? <DisplayEvidence item={selected} /> : undefined}
          fields={fields}
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            await command(
              action === 'criteria'
                ? '/display-audits/criteria'
                : action === 'submit'
                  ? '/display-audits'
                  : `/display-audits/${selected.id}/review`,
              action === 'criteria'
                ? {
                    name: values.name,
                    nppId: npp,
                    rules: {
                      expectedSkus: values.expected
                        .split(',')
                        .map((s) => s.trim())
                        .filter(Boolean),
                      minFacings: Number(values.minFacings),
                    },
                  }
                : action === 'submit'
                  ? {
                      storeId: Number(store?.id),
                      criteriaId: Number(values.criteriaId),
                      attachmentId: Number(values.attachmentId),
                    }
                  : { score: Number(values.score), reason: values.reason },
            );
            data.reload();
            criteria.reload();
            notify('Đã ghi nhận. Kết quả xử lý nền sẽ được cập nhật trên danh sách.');
          }}
        />
      )}
    </>
  );
}
