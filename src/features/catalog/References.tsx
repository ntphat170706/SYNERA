import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { FileSpreadsheet, Upload } from 'lucide-react';
import { useStore } from '../../shared/store';
import { api, command, download, records, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState, type FormField } from '../../shared/ApiUI';
import { PageHead, Panel, Table } from '../../shared/ui';
export default function References() {
  const { db, user, has, notify } = useStore();
  const [params] = useSearchParams();
  const importOnly = params.get('view') === 'import';
  const [kind, setKind] = useState(has('CATALOG_WRITE') ? 'CATEGORY' : 'STORE_GROUP'),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({}),
    [importKind, setImportKind] = useState('STORE'),
    [file, setFile] = useState<File | null>(null),
    [key, setKey] = useState(crypto.randomUUID()),
    [result, setResult] = useState<RecordData>({}),
    [busy, setBusy] = useState(false);
  const data = useApi<RecordData[]>(`/catalog/references?kind=${kind}`, []);
  const parentKind = kind === 'PROVINCE' ? 'REGION' : kind === 'WARD' ? 'PROVINCE' : kind;
  const parents = useApi<RecordData[]>(`/catalog/references?kind=${parentKind}`, []);
  const stores = useApi<RecordData[]>(kind === 'STORE_GROUP' ? '/stores' : null, []);
  const members = useApi<RecordData[]>(
    action === 'members' && selected.id ? `/catalog/references/${selected.id}/members` : null,
    [],
  );
  const fields: FormField[] = [
    { key: 'code', label: 'Mã', value: textValue(selected.code) },
    { key: 'name', label: 'Tên', value: textValue(selected.name) },
    {
      key: 'parentId',
      label: 'Danh mục cha',
      value: Number(selected.parent_id) || 0,
      options: [
        { value: 0, label: 'Không có cha' },
        ...parents.data
          .filter((p) => p.id !== selected.id)
          .map((p) => ({ value: textValue(p.id), label: textValue(p.name) })),
      ],
    },
    {
      key: 'nppId',
      label: 'Phạm vi NPP',
      value: Number(selected.npp_id) || user?.nppId || 0,
      options: [
        { value: 0, label: 'Danh mục chung' },
        ...db.partners.map((p) => ({ value: p.id, label: p.name })),
      ],
    },
    {
      key: 'active',
      label: 'Sử dụng',
      value: selected.active === false ? 'false' : 'true',
      options: [
        { value: 'true', label: 'Hoạt động' },
        { value: 'false', label: 'Ngừng sử dụng' },
      ],
    },
    { key: 'reason', label: 'Lý do', type: 'textarea' },
  ];
  async function upload(preview: boolean) {
    if (!file) return;
    setBusy(true);
    try {
      const form = new FormData();
      form.set('kind', importKind);
      form.set('preview', String(preview));
      form.set('file', file);
      setResult(await api<RecordData>('/imports', { body: form, key }));
      if (!preview) {
        window.dispatchEvent(new Event('synera:data-changed'));
        notify('Đã xử lý từng dòng. Dòng lỗi được giữ trong báo cáo bên dưới.');
      }
    } catch (e) {
      notify(e instanceof Error ? e.message : 'Không đọc được tệp.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHead title={importOnly ? 'Nhập dữ liệu Excel' : 'Danh mục & nhập Excel'}>
        {importOnly ? (
          <Link className="btn" to="/references">
            Danh mục
          </Link>
        ) : (
          <button
            className="btn primary"
            onClick={() => {
              setSelected({});
              setAction('reference');
            }}
          >
            Thêm danh mục
          </button>
        )}
      </PageHead>
      {!importOnly && (
        <>
          <label>
            Loại danh mục
            <select value={kind} onChange={(e) => setKind(e.target.value)}>
              {(has('CATALOG_WRITE')
                ? [
                    'CATEGORY',
                    'BRAND',
                    'REGION',
                    'PROVINCE',
                    'WARD',
                    'STORE_GROUP',
                    'NPP_GROUP',
                    'REASON',
                  ]
                : ['STORE_GROUP']
              ).map((k) => (
                <option key={k}>{k}</option>
              ))}
            </select>
          </label>
          <LoadState {...data} retry={data.reload} />
          <Panel>
            <Table
              rows={data.data}
              columns={[
                { key: 'code', label: 'Mã' },
                { key: 'name', label: 'Tên' },
                { key: 'parent_id', label: 'Cha' },
                { key: 'npp_id', label: 'NPP' },
                { key: 'active', label: 'Hoạt động' },
                {
                  key: 'action',
                  label: 'Sửa',
                  render: (r: RecordData) => (
                    <span className="toolbar">
                      {['STORE_GROUP', 'NPP_GROUP'].includes(kind) &&
                        (has('ROUTE_WRITE') || has('CATALOG_WRITE')) && (
                          <button
                            className="btn"
                            onClick={() => {
                              setSelected(r);
                              setAction('members');
                            }}
                          >
                            Thành viên
                          </button>
                        )}
                      <button
                        className="btn"
                        onClick={() => {
                          setSelected(r);
                          setAction('reference');
                        }}
                      >
                        Cập nhật
                      </button>
                    </span>
                  ),
                },
              ].map((c) => ({
                ...c,
                render: c.render || ((r: RecordData) => textValue(r[c.key])),
              }))}
            />
          </Panel>
        </>
      )}
      <Panel title="Dữ liệu nhập" className="npp-excel-panel">
        <div className="toolbar">
          <label>
            Dữ liệu
            <select
              value={importKind}
              onChange={(e) => {
                setImportKind(e.target.value);
                setFile(null);
                setResult({});
                setKey(crypto.randomUUID());
              }}
            >
              <option value="STORE">Cửa hàng</option>
              {has('WAREHOUSE_WRITE') && <option value="STOCK">Tồn theo lô</option>}
              {has('CATALOG_WRITE') && <option value="SKU">Sản phẩm</option>}
            </select>
          </label>
          <button
            className="btn"
            onClick={() =>
              void download(
                `/imports/template.xlsx?kind=${importKind}`,
                `synera-${importKind}.xlsx`,
              ).catch((e) => notify(e.message))
            }
          >
            Tải mẫu
          </button>
          <label className="npp-file-picker">
            <Upload size={17} />
            <span>{file?.name || 'Chọn tệp Excel'}</span>
            <input
              aria-label="Tệp Excel"
              type="file"
              accept=".xlsx"
              onChange={(e) => {
                setFile(e.target.files?.[0] || null);
                setResult({});
                setKey(crypto.randomUUID());
              }}
            />
          </label>
          <button disabled={busy || !file} className="btn" onClick={() => void upload(true)}>
            Kiểm tra tệp
          </button>
          <button
            disabled={busy || !file || result.preview !== true}
            className="btn primary"
            onClick={() => void upload(false)}
          >
            Nhập các dòng hợp lệ
          </button>
        </div>
        {result.rows ? (
          <Table
            rows={records(result.rows)}
            columns={[
              { key: 'row', label: 'Dòng', render: (r) => textValue(r.row) },
              { key: 'status', label: 'Kết quả', render: (r) => textValue(r.status) },
              { key: 'error', label: 'Lỗi cần sửa', render: (r) => textValue(r.error) },
            ]}
          />
        ) : (
          <div className="npp-clear-state">
            <FileSpreadsheet size={23} />
            <span>{file ? file.name : 'Chưa chọn tệp'}</span>
          </div>
        )}
      </Panel>
      {action && (
        <ActionForm
          title="Danh mục"
          fields={
            action === 'members'
              ? [
                  {
                    key: 'memberIds',
                    label: kind === 'STORE_GROUP' ? 'Cửa hàng' : 'NPP',
                    type: 'multiselect',
                    options:
                      kind === 'STORE_GROUP'
                        ? stores.data
                            .filter(
                              (s) =>
                                !selected.npp_id || Number(s.nppId) === Number(selected.npp_id),
                            )
                            .map((s) => ({ value: textValue(s.id), label: textValue(s.name) }))
                        : db.partners.map((p) => ({ value: p.id, label: p.name })),
                  },
                  {
                    key: 'remove',
                    label: 'Thao tác',
                    options: [
                      { value: 'false', label: 'Thêm vào nhóm' },
                      { value: 'true', label: 'Gỡ khỏi nhóm' },
                    ],
                  },
                  { key: 'reason', label: 'Lý do', type: 'textarea' },
                ]
              : fields
          }
          intro={
            action === 'members' ? (
              <Table
                rows={members.data}
                columns={[
                  { key: 'member_type', label: 'Loại', render: (r) => textValue(r.member_type) },
                  {
                    key: 'member_id',
                    label: 'Mã thành viên hiện có',
                    render: (r) => textValue(r.member_id),
                  },
                ]}
              />
            ) : undefined
          }
          onClose={() => setAction('')}
          onSubmit={async (values) => {
            if (action === 'members') {
              await command(`/catalog/references/${selected.id}/members`, {
                memberIds: values.memberIds.split(',').map(Number),
                remove: values.remove === 'true',
                reason: values.reason,
              });
              members.reload();
              return;
            }
            await command(`/catalog/references${selected.id ? '/' + selected.id : ''}`, {
              ...values,
              kind,
              parentId: Number(values.parentId) || null,
              nppId: Number(values.nppId) || null,
              active: values.active === 'true',
              version: selected.version ?? null,
            });
            data.reload();
            parents.reload();
            notify('Đã lưu danh mục.');
          }}
        />
      )}
    </>
  );
}
