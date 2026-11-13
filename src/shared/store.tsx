import {
  createContext,
  useCallback,
  useContext,
  useState,
  useEffect,
  useRef,
  type ReactNode,
} from 'react';
import { offlineDB, offlineIdentity, rememberIdentity } from './offline';
export { offlineDB, type OfflineDraft } from './offline';
import {
  api,
  browserSession,
  forgetSession,
  restoreSession,
  sessionListener,
  type SessionUser,
  type SessionResult,
  type RecordData,
  records,
  textValue,
  numberValue,
} from './api';
import type { DB, Product, Role, Status } from './types';
const emptyDB = (): DB => ({
  orders: [],
  partners: [],
  stores: [],
  tasks: [],
  tickets: [],
  policies: [],
  logs: [],
  stock: {},
  period: 'OPEN',
  visits: [],
  reportSubmitted: false,
});
type StoreContext = {
  db: DB;
  products: Product[];
  user: SessionUser | null;
  role: Role;
  initializing: boolean;
  sessionError: string;
  online: boolean;
  has: (permission: string) => boolean;
  refresh: () => Promise<void>;
  completeLogin: (result: SessionResult) => Promise<void>;
  logout: () => Promise<void>;
  notify: (message: string) => void;
  sync: () => Promise<void>;
};
const Context = createContext<StoreContext>(null!);
function group(user: SessionUser | null): Role {
  if (!user) return 'nsx';
  if (user.role === 'SALES') return 'sales';
  return user.nppId ? 'npp' : 'nsx';
}
export function Provider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null),
    [initializing, setInitializing] = useState(true),
    [sessionError, setSessionError] = useState('');
  const [db, setProjection] = useState<DB>(emptyDB),
    [products, setProducts] = useState<Product[]>([]),
    [message, notify] = useState(''),
    [online, setOnline] = useState(navigator.onLine);
  const role = group(user);
  const activeScope = useRef('');
  const syncJob = useRef<Promise<void> | null>(null);
  activeScope.current = `${user?.id || ''}:${user?.workspaceKey || ''}`;
  const refresh = useCallback(async () => {
    if (!user || ['DRIVER', 'STORE'].includes(user.role)) return;
    const salesOnly = user.role === 'SALES';
    const [catalog, partners, stores, orders] = await Promise.all([
      api<RecordData[]>('/catalog/skus'),
      api<RecordData[]>('/organizations'),
      api<RecordData[]>('/stores'),
      api<RecordData[]>(salesOnly ? '/sales-orders' : '/sell-in-orders').catch((e) => {
        if (e.status === 403 || !navigator.onLine) return [];
        throw e;
      }),
    ]);
    if (activeScope.current !== `${user.id}:${user.workspaceKey || ''}`) return;
    setProducts(
      catalog.map((p) => ({
        id: textValue(p.id),
        name: textValue(p.name),
        category: textValue(p.category),
        unit: textValue(p.baseUnit),
        price: 0,
        stock: 0,
        color: 'blue',
        short: textValue(p.code),
      })),
    );
    setProjection((current) => ({
      ...current,
      partners: partners.map((p) => ({
        id: textValue(p.id),
        name: textValue(p.name),
        contact: '',
        region: textValue(p.region),
        credit: 0,
        debt: 0,
        overdue: 0,
        revenue: 0,
        target: 0,
      })),
      stores: stores.map((s) => ({
        id: textValue(s.id),
        name: textValue(s.name),
        owner: textValue(s.owner),
        phone: textValue(s.phone),
        address: textValue(s.address),
        group: textValue(s.groupName),
        status: textValue(s.status),
        revenue: 0,
        rep: textValue(s.repId),
        lat: numberValue(s.latitude),
        lng: numberValue(s.longitude),
      })),
      orders: orders.map((o) => ({
        id: textValue(o.id),
        partnerId: textValue(o.nppId),
        date: textValue(o.createdAt),
        status: textValue(o.status) as Status,
        lines: [],
        channel: salesOnly ? 'sell-out' : 'sell-in',
        history: [],
        idempotencyKey: '',
      })),
    }));
  }, [user]);
  async function completeLogin(result: SessionResult) {
    if (!result.user || !result.accessToken) throw new Error('Phiên đăng nhập không hợp lệ.');
    setUser(result.user);
    activeScope.current = `${result.user.id}:${result.user.workspaceKey || ''}`;
    setProjection(emptyDB());
    setProducts([]);
    setSessionError('');
  }
  async function logout() {
    try {
      await browserSession('logout');
    } finally {
      await forgetSession();
      setUser(null);
      setProjection(emptyDB());
      setProducts([]);
    }
  }
  const sync = useCallback((): Promise<void> => {
    if (!user || !navigator.onLine) return Promise.resolve();
    if (syncJob.current) return syncJob.current;
    const job = (async () => {
      const restored = await restoreSession();
      if (
        !restored?.user ||
        restored.user.id !== user.id ||
        restored.user.workspaceKey !== user.workspaceKey
      ) {
        notify('Phiên hoặc phạm vi đã đổi. Đăng nhập đúng tài khoản để gửi các nháp đã lưu.');
        return;
      }
      const drafts = (await offlineDB.drafts.where('ownerId').equals(user.id).toArray())
        .filter((d) => d.serverPayload && !d.syncError && d.workspaceKey === user.workspaceKey)
        .slice(0, 50);
      if (!drafts.length) return;
      const result = await api<RecordData[]>('/sales-orders/sync', {
        body: drafts.map((d) => d.serverPayload),
      });
      const acknowledgements: string[] = [];
      for (const item of result) {
        const draft = drafts.find((d) => d.id === textValue(item.clientId));
        if (!draft) continue;
        if (item.status === 'CREATED' || item.status === 'DUPLICATE')
          acknowledgements.push(draft.id);
        else
          await offlineDB.drafts.update(draft.id, {
            syncError: textValue(item.message) || textValue(item.status),
            serverPrice: item.serverPrice,
          });
      }
      await offlineDB.drafts.bulkDelete(acknowledgements);
      window.dispatchEvent(new Event('synera:drafts-changed'));
      await refresh();
      notify(
        `Đã gửi ${acknowledgements.length}/${drafts.length} đơn. Các đơn cần kiểm tra vẫn được giữ trên thiết bị.`,
      );
    })();
    syncJob.current = job;
    const clear = () => {
      if (syncJob.current === job) syncJob.current = null;
    };
    void job.then(clear, clear);
    return job;
  }, [user, refresh]);
  useEffect(
    () =>
      sessionListener(
        () => {
          setUser(null);
          setProjection(emptyDB());
          notify('Phiên đã hết hạn. Vui lòng đăng nhập lại.');
        },
        (updated) => {
          activeScope.current = `${updated.id}:${updated.workspaceKey || ''}`;
          setUser((old) => (JSON.stringify(old) === JSON.stringify(updated) ? old : updated));
        },
      ),
    [],
  );
  useEffect(() => {
    let active = true;
    (navigator.onLine
      ? restoreSession()
      : offlineIdentity().then((user) => (user ? { user } : null))
    )
      .then((result) => {
        if (active && result?.user) setUser(result.user);
      })
      .catch((e) => {
        if (active) setSessionError(e.message);
      })
      .finally(() => {
        if (active) setInitializing(false);
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    if (user) void refresh().catch((e) => notify(e.message));
  }, [user, refresh]);
  useEffect(() => {
    const up = () => {
      setOnline(true);
      void sync().catch((e) => notify(e.message));
    };
    const down = () => setOnline(false);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    return () => {
      window.removeEventListener('online', up);
      window.removeEventListener('offline', down);
    };
  }, [sync]);
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => notify(''), 6000);
    return () => clearTimeout(timer);
  }, [message]);
  return (
    <Context.Provider
      value={{
        db,
        products,
        user,
        role,
        initializing,
        sessionError,
        online,
        has: (p) => Boolean(user?.authorities.includes(p)),
        refresh,
        completeLogin,
        logout,
        notify,
        sync,
      }}
    >
      {children}
      {message && (
        <div className="toast" role="status">
          <span>{message}</span>
          <button aria-label="Đóng thông báo" onClick={() => notify('')}>
            ×
          </button>
        </div>
      )}
    </Context.Provider>
  );
}
export const useStore = () => useContext(Context);
