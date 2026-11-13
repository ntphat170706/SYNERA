import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapPin, RefreshCw, Plus } from 'lucide-react';
import { useStore, offlineDB, type OfflineDraft } from '../../shared/store';
import { command, numberValue, textValue, type RecordData } from '../../shared/api';
import { useApi } from '../../shared/hooks/useApi';
import { ActionForm, LoadState } from '../../shared/ApiUI';
import { Panel, PageHead, Table, Badge } from '../../shared/ui';
import { money, today } from '../../shared/utils';
export default function Sales() {
  const { user, db, sync, notify, refresh } = useStore();
  const navigate = useNavigate();
  const visits = useApi<RecordData[]>('/visits/today', []);
  const [drafts, setDrafts] = useState<OfflineDraft[]>([]),
    [action, setAction] = useState(''),
    [selected, setSelected] = useState<RecordData>({}),
    [report, setReport] = useState<RecordData>({}),
    [busy, setBusy] = useState(false);
  const loadDrafts = () =>
    offlineDB.drafts
      .where('ownerId')
      .equals(user!.id)
      .toArray()
      .then((values) =>
        setDrafts(values.filter((d) => !d.workspaceKey || d.workspaceKey === user?.workspaceKey)),
      );
  useEffect(() => {
    void loadDrafts();
    const updated = () => void loadDrafts();
    window.addEventListener('synera:drafts-changed', updated);
    return () => window.removeEventListener('synera:drafts-changed', updated);
  }, [user?.id, user?.workspaceKey]);
  async function synchronize() {
    setBusy(true);
    try {
      await sync();
      await loadDrafts();
    } catch (e) {
      notify(e instanceof Error ? e.message : 'Không thể đồng bộ.');
    } finally {
      setBusy(false);
    }
  }
  async function submit(values: Record<string, string>) {
    if (action === 'check-in') {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        if (!navigator.geolocation) {
          reject(new Error('Trình duyệt không hỗ trợ vị trí.'));
          return;
        }
        navigator.geolocation.getCurrentPosition(
          resolve,
          () => reject(new Error('Không lấy được GPS. Hãy cho phép truy cập vị trí và thử lại.')),
          { enableHighAccuracy: true, timeout: 15000, maximumAge: 15000 },
        );
      });
      await command(`/visits/${selected.id}/check-in`, {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        reason: values.reason,
      });
    } else if (action === 'check-out') await command(`/visits/${selected.id}/check-out`, values);
    else
      setReport(
        await command('/sales/daily-report', {
          day: today(),
          note: values.reason,
          submit: action === 'submit-report',
        }),
      );
    visits.reload();
    await refresh();
    notify('Đã ghi nhận kết quả trên máy chủ.');
  }
  return (
    <>
      <PageHead
        title="Sales · Hôm nay"
        description={`${today()} · ${user?.name}. Lượt ghé, GPS và kết quả bán hàng theo phân công.`}
      >
        <button className="btn" disabled={busy} onClick={() => void synchronize()}>
          <RefreshCw size={16} />
          {busy ? 'Đang gửi...' : 'Đồng bộ nháp'}
        </button>
        <button className="btn primary" onClick={() => navigate('/catalog')}>
          <Plus size={16} />
          Tạo đơn nhanh
        </button>
      </PageHead>
      <LoadState {...visits} retry={visits.reload} />
      <Panel title="Lịch ghé hôm nay">
        <Table
          rows={visits.data}
          columns={[
            {
              key: 'store',
              label: 'Cửa hàng',
              render: (v) =>
                db.stores.find((s) => s.id === textValue(v.storeId))?.name ||
                `Cửa hàng #${v.storeId}`,
            },
            {
              key: 'status',
              label: 'Trạng thái',
              render: (v) => <Badge status={textValue(v.status)} />,
            },
            {
              key: 'distance',
              label: 'Khoảng cách khi vào',
              render: (v) =>
                `${numberValue(v.distanceMetres).toFixed(0)} m ${v.outsideRadius ? '· Ngoài bán kính' : ''}`,
            },
            {
              key: 'action',
              label: 'Thao tác',
              render: (v) => (
                <div className="toolbar">
                  {['PLANNED', 'CHECKED_IN'].includes(textValue(v.status)) && (
                    <button
                      className="btn"
                      onClick={() => {
                        setSelected(v);
                        setAction(v.status === 'PLANNED' ? 'check-in' : 'check-out');
                      }}
                    >
                      <MapPin size={15} />
                      {v.status === 'PLANNED' ? 'Check-in' : 'Check-out'}
                    </button>
                  )}
                  <button className="btn" onClick={() => navigate('/crm?store=' + v.storeId)}>
                    Hồ sơ
                  </button>
                </div>
              ),
            },
          ]}
        />
      </Panel>
      <Panel
        title={`Nháp trên thiết bị · ${drafts.length}`}
        subtitle="Chỉ các đơn thuộc tài khoản hiện tại được hiển thị và gửi đi."
      >
        <Table
          rows={drafts}
          columns={[
            { key: 'id', label: 'Mã nháp', render: (d) => d.id.slice(0, 8) },
            {
              key: 'store',
              label: 'Cửa hàng',
              render: (d) => db.stores.find((s) => s.id === d.partnerId)?.name || d.partnerId,
            },
            {
              key: 'status',
              label: 'Kết quả',
              render: (d) =>
                d.syncError ? (
                  <span role="alert">{d.syncError}</span>
                ) : (
                  <Badge status="PENDING_SYNC" />
                ),
            },
            {
              key: 'action',
              label: 'Thao tác',
              render: (d) => (
                <button
                  className="btn"
                  onClick={() => navigate(`/catalog?store=${d.partnerId}&draft=${d.id}`)}
                >
                  Mở để kiểm tra / chỉnh đơn
                </button>
              ),
            },
          ]}
        />
      </Panel>
      <Panel
        title="Báo cáo cuối ngày"
        subtitle="Số đơn và lượt ghé được tổng hợp từ dữ liệu đã ghi nhận."
      >
        <div className="toolbar">
          <button className="btn" onClick={() => setAction('draft-report')}>
            Xem nháp báo cáo
          </button>
          <button className="btn primary" onClick={() => setAction('submit-report')}>
            Nộp báo cáo
          </button>
        </div>
        {Boolean(report.id) && (
          <div className="info-box">
            <Badge status={textValue(report.status)} /> · {numberValue(report.visits)} lượt ghé ·{' '}
            {numberValue(report.orders)} đơn · {money(numberValue(report.amount))}
            <p>{textValue(report.note)}</p>
          </div>
        )}
      </Panel>
      {action && (
        <ActionForm
          title={
            action === 'check-in'
              ? 'Check-in bằng GPS'
              : action === 'check-out'
                ? 'Kết quả lượt ghé'
                : 'Báo cáo cuối ngày'
          }
          fields={[
            {
              key: 'reason',
              label:
                action === 'check-in' ? 'Ghi chú / lý do nếu ngoài bán kính' : 'Nội dung kết quả',
              type: 'textarea',
              required: action !== 'check-in',
            },
          ]}
          onClose={() => setAction('')}
          onSubmit={submit}
        />
      )}
    </>
  );
}
