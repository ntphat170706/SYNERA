import { useCallback } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  Building2,
  ChevronDown,
  HelpCircle,
  ArrowUpRight,
  Layers,
  PanelLeftClose,
  PanelLeftOpen,
  X,
  Globe,
  ArrowRightLeft,
  LayoutDashboard,
  ShoppingBag,
  Tags,
  BarChart3,
  Settings,
} from 'lucide-react';
import { navigation, nppNavigation, canVisit, homeRoute } from './navigation';
import { useStore } from '../shared/store';
import { Brand } from '../shared/Brand';
import { useDrawer } from '../shared/hooks/useDrawer';

type Props = {
  mobile: boolean;
  collapsed: boolean;
  onClose: () => void;
  onCollapse: () => void;
  onModal: (name: string) => void;
};
export function Sidebar({ mobile, collapsed, onClose, onCollapse, onModal }: Props) {
  const { db, role, user } = useStore();
  const location = useLocation();
  const close = useCallback(onClose, [onClose]);
  const ref = useDrawer(mobile, close);
  const pending = db.orders.filter((o) =>
    ['PENDING_APPROVAL', 'ON_HOLD_CREDIT'].includes(o.status),
  ).length;
  return (
    <>
      <aside
        ref={ref}
        tabIndex={-1}
        aria-label="Menu quản trị"
        role={mobile ? 'dialog' : undefined}
        aria-modal={mobile || undefined}
        className={`sidebar ${mobile ? 'open' : ''}`}
      >
        <div className="sidebar-brand-row">
          <NavLink
            className="brand"
            to={homeRoute(user)}
            aria-label="SYNERA · Tổng quan"
            onClick={onClose}
          >
            <Brand />
          </NavLink>
          <button className="icon-button drawer-close" aria-label="Đóng menu" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        <button
          className="workspace"
          title="Chọn không gian làm việc"
          onClick={() => onModal('workspace')}
        >
          <span className="workspace-icon">
            <Building2 size={18} />
          </span>
          <span>
            <strong>Không gian SYNERA</strong>
            <small>
              {role === 'npp'
                ? db.partners.find((p) => p.id === String(user?.nppId))?.name ||
                  `NPP ${user?.nppId}`
                : role === 'sales'
                  ? 'Đội ngũ bán hàng'
                  : 'Hệ thống phân phối'}
            </small>
          </span>
          <ChevronDown size={15} />
        </button>
        <nav aria-label="Điều hướng chính">
          {role === 'npp' ? (
            <div className="npp-primary-nav">
              {nppNavigation
                .filter((item) => canVisit(item.path, user))
                .map(({ path, label, icon: Icon, children }) => (
                  <NavLink
                    key={path}
                    to={path}
                    title={label}
                    onClick={onClose}
                    className={`nav-link ${location.pathname === path || children.includes(location.pathname) ? 'active' : ''}`}
                  >
                    <Icon size={21} stroke={1.75} />
                    <span>{label}</span>
                  </NavLink>
                ))}
            </div>
          ) : role === 'nsx' ? (
            <div className="nsx-primary-nav">
              {[
                ['/dashboard', 'Điều hành', LayoutDashboard],
                ['/network', 'Mạng lưới', Building2],
                ['/orders', 'Đơn hàng', ShoppingBag],
                ['/policies', 'Chính sách', Tags],
                ['/reports', 'Báo cáo', BarChart3],
                ['/settings', 'Thiết lập', Settings],
              ]
                .filter(([path]) => canVisit(String(path), user))
                .map(([path, label, Icon]) => {
                  const ItemIcon = Icon as typeof Building2;
                  return (
                    <NavLink
                      key={String(path)}
                      to={String(path)}
                      title={String(label)}
                      onClick={onClose}
                      className={`nav-link ${location.pathname === path || (path === '/network' && ['/partners', '/crm', '/routes'].includes(location.pathname)) ? 'active' : ''}`}
                    >
                      <ItemIcon size={21} />
                      <span>{String(label)}</span>
                    </NavLink>
                  );
                })}
            </div>
          ) : (
            <>
              {canVisit('/npp-workflow', user) && (
                <NavLink
                  to="/npp-workflow"
                  onClick={onClose}
                  title="Trung tâm phối hợp"
                  className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                >
                  <ArrowRightLeft size={19} />
                  <span>Trung tâm phối hợp</span>
                </NavLink>
              )}
              {navigation.map((group) => (
                <div className="nav-group" key={group.title}>
                  <p>{group.title}</p>
                  {group.items
                    .filter((i) => canVisit(i[0], user))
                    .map(([path, label, Icon]) => (
                      <NavLink
                        key={path}
                        to={path}
                        title={label}
                        onClick={onClose}
                        className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                      >
                        <Icon size={19} />
                        <span>{label}</span>
                        {path === '/orders' && pending > 0 && <b>{pending}</b>}
                        {path === '/sales' && <span className="nav-new">PWA</span>}
                      </NavLink>
                    ))}
                </div>
              ))}
            </>
          )}
        </nav>
        <div className="sidebar-bottom">
          {role !== 'npp' && (
            <NavLink
              to="/landing"
              className="sidebar-help"
              title="Website SYNERA"
              onClick={onClose}
            >
              <Globe size={18} />
              <span>Website SYNERA</span>
              <ArrowUpRight size={14} />
            </NavLink>
          )}
          <button
            className="sidebar-help"
            title="Trợ giúp & hướng dẫn"
            onClick={() => onModal('help')}
          >
            <HelpCircle size={18} />
            <span>Trợ giúp & hướng dẫn</span>
            <ArrowUpRight size={14} />
          </button>
          <button
            className="collapse-control"
            aria-label={collapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'}
            onClick={onCollapse}
          >
            {collapsed ? <PanelLeftOpen size={19} /> : <PanelLeftClose size={19} />}
            <span>Thu gọn menu</span>
          </button>
          {role !== 'npp' && (
            <div className="sidebar-version">
              <Layers size={13} />
              <span>Không gian SYNERA</span>
              <span>v1.0</span>
            </div>
          )}
        </div>
      </aside>
      {mobile && <div className="sidebar-scrim" onClick={onClose} aria-hidden="true" />}
    </>
  );
}
