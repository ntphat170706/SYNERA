import { useState, useCallback, lazy, Suspense, Component, type ReactNode } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import {
  Search,
  Bell,
  ChevronDown,
  Sun,
  Moon,
  Menu,
  ChevronRight,
  WifiOff,
  RefreshCw,
} from 'lucide-react';
import { Provider, useStore } from '../shared/store';
import { Modal, SearchBox, Empty } from '../shared/ui';
import { ActionForm } from '../shared/ApiUI';
import { api, browserSession, command, textValue, type RecordData } from '../shared/api';
import { useApi } from '../shared/hooks/useApi';
import {
  navigation,
  nppContextLinks,
  nsxContextLinks,
  canVisit,
  homeRoute,
  roleLabels,
} from './navigation';
import { Sidebar } from './Sidebar';
import WorkspacePanel from './WorkspacePanel';
import { notificationInfo, notificationTime } from '../shared/notifications';
const Landing = lazy(() => import('../features/landing/Landing'));
const Login = lazy(() => import('../features/auth/Login'));
const Dashboard = lazy(() => import('../features/reports/Dashboard'));
const Orders = lazy(() => import('../features/orders/Orders'));
const Catalog = lazy(() => import('../features/portal-npp/Catalog'));
const NppOverview = lazy(() => import('../features/portal-npp/Overview'));
const NppWorkflow = lazy(() => import('../features/portal-npp/Workflow'));
const NsxNetwork = lazy(() => import('../features/portal-nsx/Network'));
const NsxReports = lazy(() => import('../features/portal-nsx/Reports'));
const NsxControlRoom = lazy(() => import('../features/portal-nsx/ControlRoom'));
const Exports = lazy(() => import('../features/reports/Exports'));
const Finance = lazy(() => import('../features/finance/Finance'));
const CRM = lazy(() => import('../features/crm/CRM'));
const Sales = lazy(() => import('../features/sales-app/Sales'));
const Management = lazy(() => import('../features/management/Management'));
const Demand = lazy(() => import('../features/demand/Demand'));
const Trade = lazy(() => import('../features/trade/Trade'));
const Engagement = lazy(() => import('../features/crm/Engagement'));
const Delivery = lazy(() => import('../features/delivery/Delivery'));
const Display = lazy(() => import('../features/display/Display'));
const References = lazy(() => import('../features/catalog/References'));
class ErrorBoundary extends Component<{ children: ReactNode }, { error: boolean }> {
  state = { error: false };
  static getDerivedStateFromError() {
    return { error: true };
  }
  render() {
    return this.state.error ? (
      <div className="empty">
        <h2>Không thể tải màn hình</h2>
        <p>Hãy tải lại trang. Dữ liệu đã lưu trên máy chủ được giữ nguyên.</p>
        <button className="btn primary" onClick={() => location.reload()}>
          Tải lại
        </button>
      </div>
    ) : (
      this.props.children
    );
  }
}
function Shell() {
  const {
    db,
    products,
    user,
    role,
    initializing,
    online,
    sync,
    logout,
    notify,
    refresh,
    completeLogin,
  } = useStore();
  const [mobile, setMobile] = useState(false),
    [collapsed, setCollapsed] = useState(
      () => localStorage.getItem('synera-sidebar-collapsed') === 'true',
    ),
    [dark, setDark] = useState(() => localStorage.getItem('synera-dark') === 'true');
  const [modal, setModal] = useState(''),
    [query, setQuery] = useState('');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const closeMobile = useCallback(() => setMobile(false), []);
  const location = useLocation(),
    navigate = useNavigate();
  const notifications = useApi<RecordData[]>(user ? '/notifications' : null, []);
  const workspaces = useApi<RecordData[]>(user ? '/auth/session/workspaces' : null, []);
  const current =
    (role === 'nsx'
      ? (
          {
            '/network': 'Mạng lưới phân phối',
            '/dashboard': 'Điều hành hôm nay',
            '/reports': 'Báo cáo đi tuyến & bán hàng',
          } as Record<string, string>
        )[location.pathname]
      : '') ||
    (role === 'npp'
      ? (
          {
            '/finance': 'Công nợ với NSX',
            '/trade': 'Hỗ trợ bán hàng',
            '/crm': 'Điểm bán & CRM',
          } as Record<string, string>
        )[location.pathname]
      : '') ||
    navigation.flatMap((group) => group.items).find((item) => item[0] === location.pathname)?.[1] ||
    (location.pathname === '/npp-workflow' ? 'Giao thiếu & phản hồi khách' : 'Không gian làm việc');
  const fallback = (
    <div className="loading" role="status">
      Đang tải SYNERA...
    </div>
  );
  if (location.pathname === '/' || location.pathname === '/landing')
    return (
      <Suspense fallback={fallback}>
        <Landing />
      </Suspense>
    );
  if (initializing) return fallback;
  if (location.pathname === '/login')
    return user ? (
      <Navigate to={homeRoute(user)} replace />
    ) : (
      <Suspense fallback={fallback}>
        <Login />
      </Suspense>
    );
  if (!user) return <Navigate to="/login" replace />;
  if (!canVisit(location.pathname, user)) return <Navigate to={homeRoute(user)} replace />;
  async function signOut() {
    try {
      await logout();
    } catch {
      notify('Phiên đã được đóng trên trình duyệt.');
    }
    navigate('/login');
  }
  return (
    <div
      className={`app ${role === 'npp' ? 'npp-portal' : role === 'nsx' ? 'nsx-portal' : ''} ${dark ? 'dark' : ''} ${collapsed ? 'sidebar-collapsed' : ''}`}
    >
      <Sidebar
        mobile={mobile}
        collapsed={collapsed}
        onClose={closeMobile}
        onCollapse={() => {
          setCollapsed(!collapsed);
          localStorage.setItem('synera-sidebar-collapsed', String(!collapsed));
        }}
        onModal={setModal}
      />
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-menu"
              aria-label="Mở menu"
              onClick={() => setMobile(!mobile)}
            >
              <Menu size={20} />
            </button>
            <span>Không gian làm việc</span>
            <ChevronRight size={14} />
            <strong>{current}</strong>
          </div>
          <div className="topbar-actions">
            <button className="global-search" onClick={() => setModal('search')}>
              <Search size={17} />
              <span>Tìm kiếm nhanh...</span>
            </button>
            <button
              className="icon-button"
              aria-label={dark ? 'Chế độ sáng' : 'Chế độ tối'}
              onClick={() => {
                setDark(!dark);
                localStorage.setItem('synera-dark', String(!dark));
              }}
            >
              {dark ? <Sun size={19} /> : <Moon size={19} />}
            </button>
            <button
              className="icon-button notification-button"
              aria-label="Thông báo"
              onClick={() => {
                notifications.reload();
                setModal('notifications');
              }}
            >
              <Bell size={19} />
              {notifications.data.some((n) => !n.read) && <i />}
            </button>
            <span className="topbar-divider" />
            <button className="user-button" onClick={() => setModal('profile')}>
              <span className="avatar">
                {user.name
                  .split(' ')
                  .map((n) => n[0])
                  .slice(-2)
                  .join('')}
              </span>
              <span>
                <strong>{user.name}</strong>
                <small>{roleLabels[role]}</small>
              </span>
              <ChevronDown size={14} />
            </button>
          </div>
        </header>
        {role === 'sales' && (
          <div className="demo-strip">
            <span>
              <span className="online-dot" />
              {roleLabels[role]} · {user.name}
            </span>
            <button
              className="text-button"
              onClick={() => void refresh().catch((e) => notify(e.message))}
            >
              <RefreshCw size={14} /> Cập nhật
            </button>
          </div>
        )}
        {!online && (
          <div className="offline-strip">
            <WifiOff size={16} />
            Bạn đang ngoại tuyến. Nháp Sales được giữ trên thiết bị.
            <button onClick={() => void sync().catch((e) => notify(e.message))}>Thử đồng bộ</button>
          </div>
        )}
        <main data-page={location.pathname.slice(1)}>
          {role === 'nsx' && nsxContextLinks[location.pathname] && (
            <nav className="nsx-context-links" aria-label="Tác vụ liên quan">
              {nsxContextLinks[location.pathname]
                .filter(([path]) => canVisit(path.split('?')[0], user))
                .map(([path, label]) => (
                  <Link key={path} to={path}>
                    {label}
                    <ChevronRight size={13} />
                  </Link>
                ))}
            </nav>
          )}
          {role === 'npp' &&
            nppContextLinks[location.pathname]?.some(([path]) =>
              canVisit(path.split('?')[0], user),
            ) && (
              <nav className="npp-section-links" aria-label="Tác vụ liên quan">
                {nppContextLinks[location.pathname]
                  .filter(([path]) => canVisit(path.split('?')[0], user))
                  .map(([path, label]) => (
                    <Link key={path} to={path}>
                      {label}
                      <ChevronRight size={14} />
                    </Link>
                  ))}
              </nav>
            )}
          <ErrorBoundary>
            <Suspense fallback={fallback}>
              <Routes>
                <Route
                  path="/account"
                  element={
                    <div className="info-box">
                      Tài khoản đã đăng nhập. Chọn không gian làm việc được cấp trong hồ sơ; liên hệ
                      quản trị nếu chưa có quyền truy cập phân hệ.
                    </div>
                  }
                />
                <Route
                  path="/dashboard"
                  element={
                    role === 'npp' ? (
                      <NppOverview />
                    ) : role === 'nsx' ? (
                      <NsxControlRoom />
                    ) : (
                      <Dashboard />
                    )
                  }
                />
                <Route path="/npp-workflow" element={<NppWorkflow />} />
                <Route path="/orders" element={<Orders />} />
                <Route path="/catalog" element={<Catalog />} />
                <Route path="/finance" element={<Finance />} />
                <Route
                  path="/crm"
                  element={
                    role === 'nsx' &&
                    !['leads', 'appointments'].includes(
                      new URLSearchParams(location.search).get('view') || '',
                    ) ? (
                      <NsxNetwork initialView="stores" />
                    ) : (
                      <CRM />
                    )
                  }
                />
                <Route path="/network" element={<NsxNetwork />} />
                <Route path="/exports" element={<Exports />} />
                <Route path="/sales" element={<Sales />} />
                <Route path="/demand" element={<Demand />} />
                <Route path="/trade" element={<Trade />} />
                <Route path="/engagement" element={<Engagement />} />
                <Route path="/delivery" element={<Delivery />} />
                <Route path="/display" element={<Display />} />
                <Route path="/references" element={<References />} />
                {['partners', 'inventory', 'routes', 'policies', 'tickets', 'settings'].map(
                  (page) => (
                    <Route
                      key={page}
                      path={'/' + page}
                      element={
                        role === 'nsx' && ['partners', 'routes'].includes(page) ? (
                          <NsxNetwork
                            key={page}
                            initialView={page === 'routes' ? 'routes' : 'npps'}
                          />
                        ) : (
                          <Management key={page} page={page} />
                        )
                      }
                    />
                  ),
                )}
                <Route
                  path="/reports"
                  element={role === 'nsx' ? <NsxReports /> : <Dashboard reports />}
                />
              </Routes>
            </Suspense>
          </ErrorBoundary>
        </main>
        {role !== 'npp' && (
          <footer>
            <span>© 2026 SYNERA · Kết nối hệ sinh thái phân phối</span>
            <span>DMS ↔ CRM</span>
          </footer>
        )}
      </div>
      {modal === 'password' ? (
        <ActionForm
          title="Đổi mật khẩu"
          fields={[
            { key: 'oldPassword', label: 'Mật khẩu hiện tại', type: 'password' },
            { key: 'newPassword', label: 'Mật khẩu mới (12–200 ký tự)', type: 'password' },
          ]}
          onClose={() => setModal('')}
          onSubmit={async (values) => {
            await api('/auth/password', { body: values });
            await signOut();
          }}
        />
      ) : (
        modal && (
          <Modal
            title={
              modal === 'search'
                ? 'Tìm kiếm toàn cục'
                : modal === 'notifications'
                  ? 'Trung tâm thông báo'
                  : modal === 'help'
                    ? 'Hướng dẫn sử dụng'
                    : modal === 'workspace'
                      ? 'Không gian làm việc'
                      : 'Hồ sơ cá nhân'
            }
            onClose={() => setModal('')}
            className={
              modal === 'notifications'
                ? 'notification-modal'
                : modal === 'workspace' || modal === 'profile'
                  ? 'workspace-modal'
                  : ''
            }
          >
            <div className="stack">
              {(modal === 'profile' || modal === 'workspace') && (
                <WorkspacePanel
                  workspaces={workspaces.data}
                  loading={workspaces.loading}
                  error={workspaces.error}
                  onRetry={workspaces.reload}
                  onPassword={() => setModal('password')}
                  onLogout={() => void signOut()}
                  onSelect={async (key) => {
                    const result = await browserSession('workspace', { key });
                    await completeLogin(result);
                    setModal('');
                    navigate(homeRoute(result.user || null));
                  }}
                />
              )}
              {modal === 'help' && (
                <>
                  <p>
                    Tài khoản và quyền truy cập do quản trị viên cấp. Tạo đơn tại Đặt hàng, theo dõi
                    phê duyệt và giao nhận tại Đơn hàng.
                  </p>
                  <p>
                    Nháp Sales ngoại tuyến được giữ theo tài khoản; chỉ xóa khỏi hàng đợi khi máy
                    chủ xác nhận thành công. Công nợ và báo cáo đã chốt luôn lấy từ máy chủ.
                  </p>
                </>
              )}
              {modal === 'notifications' && (
                <>
                  <div className="notification-filters">
                    <button
                      className={!unreadOnly ? 'selected' : ''}
                      onClick={() => setUnreadOnly(false)}
                    >
                      Tất cả
                    </button>
                    <button
                      className={unreadOnly ? 'selected' : ''}
                      onClick={() => setUnreadOnly(true)}
                    >
                      Chưa đọc
                    </button>
                  </div>
                  {notifications.error && <p role="alert">{notifications.error}</p>}
                  {!notifications.data.length && <Empty text="Chưa có thông báo." />}
                  {notifications.data
                    .filter((n) => !unreadOnly || !n.read)
                    .slice()
                    .sort((a, b) => Number(b.id) - Number(a.id))
                    .map((n) => (
                      <button
                        className={`notification-item ${!n.read ? 'unread' : ''}`}
                        key={textValue(n.id)}
                        onClick={() => {
                          const target = notificationInfo(n).path;
                          if (target) {
                            setModal('');
                            navigate(target);
                          }
                          void command(`/notifications/${n.id}/read`)
                            .then(notifications.reload)
                            .catch((e) => notify(e.message));
                        }}
                      >
                        <span className="notification-avatar">
                          <Bell size={21} />
                        </span>
                        <span>
                          <strong>{notificationInfo(n).title}</strong>
                          <small>{notificationInfo(n).description}</small>
                          <time>{notificationTime(n.createdAt)}</time>
                        </span>
                        {!n.read && <i className="notification-unread-dot" aria-label="Chưa đọc" />}
                      </button>
                    ))}
                  {unreadOnly &&
                    notifications.data.length > 0 &&
                    !notifications.data.some((n) => !n.read) && (
                      <Empty text="Bạn đã đọc hết thông báo." />
                    )}
                </>
              )}
              {modal === 'search' && (
                <>
                  <SearchBox
                    value={query}
                    onChange={setQuery}
                    placeholder="Mã đơn, sản phẩm, cửa hàng, nhà phân phối..."
                  />
                  {query.trim() &&
                    [
                      ...db.orders.map((o) => ({
                        id: o.id,
                        name: `Đơn #${o.id}`,
                        path: '/orders?order=' + o.id,
                      })),
                      ...products.map((p) => ({
                        id: p.id,
                        name: `${p.short} · ${p.name}`,
                        path: '/catalog?q=' + encodeURIComponent(p.name),
                      })),
                      ...db.stores.map((s) => ({
                        id: s.id,
                        name: s.name,
                        path: '/crm?store=' + s.id,
                      })),
                      ...db.partners.map((p) => ({
                        id: p.id,
                        name: p.name,
                        path: '/partners?q=' + p.name,
                      })),
                    ]
                      .filter((item) =>
                        `${item.id} ${item.name}`
                          .toLocaleLowerCase('vi')
                          .includes(query.toLocaleLowerCase('vi')),
                      )
                      .slice(0, 20)
                      .map((item) => (
                        <button
                          className="search-result"
                          key={item.path}
                          onClick={() => {
                            navigate(item.path);
                            setModal('');
                          }}
                        >
                          {item.name}
                          <ChevronRight size={15} />
                        </button>
                      ))}
                </>
              )}
            </div>
          </Modal>
        )
      )}
    </div>
  );
}
export default function App() {
  return (
    <Provider>
      <Shell />
    </Provider>
  );
}
