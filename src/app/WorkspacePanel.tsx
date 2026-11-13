import { useState } from 'react';
import { Building2, Check, ChevronRight, KeyRound, LogOut, ShieldCheck } from 'lucide-react';
import { useStore } from '../shared/store';
import { textValue, type RecordData } from '../shared/api';
const roleNames: Record<string, string> = {
  NPP_OWNER: 'Chủ nhà phân phối',
  ACCOUNTANT_NPP: 'Kế toán NPP',
  WAREHOUSE_NPP: 'Kho NPP',
  ADMIN: 'Quản trị hệ thống',
  SALES: 'Nhân viên Sales',
  SUPERVISOR: 'Giám sát bán hàng',
  ACCOUNTANT_NSX: 'Kế toán NSX',
  REGIONAL_MANAGER: 'Quản lý vùng',
  DRIVER: 'Nhân viên giao hàng',
};
export default function WorkspacePanel({
  workspaces,
  loading,
  error,
  onRetry,
  onSelect,
  onPassword,
  onLogout,
}: {
  workspaces: RecordData[];
  loading: boolean;
  error: string;
  onRetry: () => void;
  onSelect: (key: string) => Promise<void>;
  onPassword: () => void;
  onLogout: () => void;
}) {
  const { user, db } = useStore();
  const [busy, setBusy] = useState(''),
    [localError, setLocalError] = useState('');
  if (!user) return null;
  const displayRole = (code: unknown) => roleNames[textValue(code)] || 'Thành viên';
  function organization(workspace: RecordData) {
    return workspace.nppId
      ? db.partners.find((p) => p.id === String(workspace.nppId))?.name || 'Nhà phân phối'
      : workspace.scopeType === 'REGION'
        ? `Vùng ${workspace.region || workspace.scopeId}`
        : 'Nhà sản xuất';
  }
  return (
    <div className="workspace-panel">
      <div className="workspace-person">
        <span className="workspace-person-avatar">
          {user.name
            .split(' ')
            .filter(Boolean)
            .slice(-2)
            .map((word) => word[0])
            .join('')}
        </span>
        <div>
          <h3>{user.name}</h3>
          <span>
            <ShieldCheck size={14} />
            {displayRole(user.roleCode || user.role)}
          </span>
        </div>
      </div>
      <div className="workspace-list">
        <h4>Đơn vị đang làm việc</h4>
        {loading && <p role="status">Đang tải...</p>}
        {(error || localError) && (
          <p role="alert" className="form-error">
            {error || localError}
            {error && (
              <button className="text-button" onClick={onRetry}>
                Thử lại
              </button>
            )}
          </p>
        )}
        {workspaces.map((workspace) => {
          const active = workspace.key === user.workspaceKey;
          return (
            <button
              key={textValue(workspace.key)}
              className={`workspace-choice ${active ? 'selected' : ''}`}
              disabled={Boolean(busy)}
              aria-pressed={active}
              onClick={async () => {
                if (active) return;
                setBusy(textValue(workspace.key));
                setLocalError('');
                try {
                  await onSelect(textValue(workspace.key));
                } catch (e) {
                  setLocalError(
                    e instanceof Error ? e.message : 'Không thể chuyển không gian làm việc.',
                  );
                } finally {
                  setBusy('');
                }
              }}
            >
              <span className="workspace-choice-icon">
                <Building2 size={21} />
              </span>
              <span>
                <strong>{organization(workspace)}</strong>
                <small>{displayRole(workspace.roleCode || workspace.role)}</small>
              </span>
              <span className="workspace-choice-end">
                {busy === workspace.key ? (
                  'Đang chuyển...'
                ) : active ? (
                  <>
                    <span>Hiện tại</span>
                    <Check size={18} />
                  </>
                ) : (
                  <ChevronRight size={18} />
                )}
              </span>
            </button>
          );
        })}
      </div>
      <div className="workspace-account-actions">
        <button type="button" onClick={onPassword}>
          <KeyRound size={17} />
          Đổi mật khẩu
          <ChevronRight size={15} />
        </button>
        <button type="button" onClick={onLogout}>
          <LogOut size={17} />
          Đăng xuất
        </button>
      </div>
    </div>
  );
}
