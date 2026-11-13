import type { Challenge, SalesUser, Session } from '../domain/contracts.ts';
export interface TokenVault {
  read(): Promise<string | null>;
  write(token: string | null): Promise<void>;
}
export class ApiFailure extends Error {
  code: string;
  status: number;
  constructor(message: string, code = 'REQUEST_FAILED', status = 0) {
    super(message);
    this.code = code;
    this.status = status;
  }
}
export function createSalesClient(
  baseUrl: string,
  vault: TokenVault,
  transport: typeof fetch = fetch,
) {
  let session: Session | null = null;
  let refreshing: Promise<void> | null = null;
  let generation = 0;
  let loggingOut = false;
  let challenge: { id: number; version: number } | null = null;
  let writing = Promise.resolve();
  function save(token: string | null) {
    const next = writing.catch(() => {}).then(() => vault.write(token));
    writing = next;
    return next;
  }
  let changed = (_user: SalesUser | null) => {};
  const origin = baseUrl.replace(/\/$/, '');
  async function request<T>(
    path: string,
    method = 'GET',
    body?: unknown,
    access?: string,
    idempotencyKey?: string,
  ): Promise<T> {
    let response: Response;
    const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 20000);
      try {
        response = await transport(origin + '/api/v1' + path, {
          method,
          signal: controller.signal,
          headers: {
            Accept: 'application/json',
            ...(body === undefined || multipart ? {} : { 'Content-Type': 'application/json' }),
            ...(access ? { Authorization: 'Bearer ' + access } : {}),
            ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
          },
          ...(body === undefined
            ? {}
            : { body: multipart ? (body as FormData) : JSON.stringify(body) }),
        });
      } finally {
        clearTimeout(timer);
      }
    } catch {
      throw new ApiFailure('Không thể kết nối. Kiểm tra mạng rồi thử lại.', 'NETWORK_ERROR');
    }
    const envelope = await response.json().catch(() => null);
    if (!response.ok || !envelope?.success)
      throw new ApiFailure(
        envelope?.error?.message || 'Không thể tải dữ liệu.',
        envelope?.error?.code,
        response.status,
      );
    return envelope.data as T;
  }
  async function accept(value: Session, expectedGeneration: number) {
    if (expectedGeneration !== generation)
      throw new ApiFailure('Phiên đã thay đổi.', 'STALE_SESSION');
    if (value.user.role !== 'SALES')
      throw new ApiFailure(
        'Ứng dụng dành cho nhân viên Sales. Hãy dùng web cho vai trò này.',
        'SALES_ONLY',
      );
    await save(value.refreshToken);
    if (expectedGeneration !== generation)
      throw new ApiFailure('Phiên đã thay đổi.', 'STALE_SESSION');
    session = value;
    changed(value.user);
  }
  async function refresh() {
    if (loggingOut) throw new ApiFailure('Phiên đang đóng.', 'STALE_SESSION');
    if (!refreshing) {
      const version = generation;
      refreshing = (async () => {
        const saved = session?.refreshToken || (await vault.read());
        if (!saved) throw new ApiFailure('Vui lòng đăng nhập.', 'UNAUTHENTICATED', 401);
        try {
          await accept(
            await request<Session>('/auth/refresh', 'POST', { refreshToken: saved }),
            version,
          );
        } catch (e) {
          if (
            version === generation &&
            e instanceof ApiFailure &&
            ([401, 403].includes(e.status) || e.code === 'SALES_ONLY')
          ) {
            session = null;
            await save(null);
            changed(null);
          }
          throw e;
        }
      })().finally(() => {
        refreshing = null;
      });
    }
    return refreshing;
  }
  async function authorized<T>(
    path: string,
    method: string,
    body?: unknown,
    key?: string,
  ): Promise<T> {
    if (loggingOut) throw new ApiFailure('Phiên đang đóng.', 'STALE_SESSION');
    if (!session) throw new ApiFailure('Vui lòng đăng nhập.', 'UNAUTHENTICATED', 401);
    const version = generation,
      access = session.accessToken;
    try {
      const value = await request<T>(path, method, body, access, key);
      if (version !== generation) throw new ApiFailure('Phiên đã thay đổi.', 'STALE_SESSION');
      return value;
    } catch (error) {
      if (!(error instanceof ApiFailure) || error.status !== 401 || version !== generation)
        throw error;
      if (!session || session.accessToken === access) await refresh();
      if (version !== generation) throw new ApiFailure('Phiên đã thay đổi.', 'STALE_SESSION');
      const value = await request<T>(path, method, body, session?.accessToken, key);
      if (version !== generation) throw new ApiFailure('Phiên đã thay đổi.', 'STALE_SESSION');
      return value;
    }
  }
  return {
    subscribe(listener: typeof changed) {
      changed = listener;
      return () => {
        changed = () => {};
      };
    },
    user() {
      return session?.user || null;
    },
    async restore() {
      if (await vault.read()) await refresh();
    },
    async login(username: string, password: string): Promise<Challenge | null> {
      const version = ++generation;
      challenge = null;
      session = null;
      changed(null);
      await save(null);
      const result = await request<Session | Challenge>('/auth/login', 'POST', {
        username: username.trim(),
        password,
      });
      if (version !== generation) throw new ApiFailure('Phiên đã thay đổi.', 'STALE_SESSION');
      if ('requiresOtp' in result) {
        challenge = { id: result.challengeId, version };
        return result;
      }
      await accept(result, version);
      return null;
    },
    async verify(challengeId: number, code: string) {
      if (challenge?.id !== challengeId || challenge.version !== generation)
        throw new ApiFailure('Lượt xác thực đã thay đổi. Đăng nhập lại.', 'STALE_SESSION');
      const version = challenge.version;
      await accept(
        await request<Session>('/auth/otp/verify', 'POST', { challengeId, code }),
        version,
      );
      challenge = null;
    },
    get<T>(path: string): Promise<T> {
      return authorized<T>(path, 'GET');
    },
    post<T>(path: string, body: unknown, key: string): Promise<T> {
      if (!key || key.length < 8)
        throw new ApiFailure('Thiếu mã thao tác. Vui lòng thử lại.', 'MISSING_IDEMPOTENCY');
      return authorized<T>(path, 'POST', body, key);
    },
    async logout() {
      if (loggingOut) return;
      const access = session?.accessToken;
      ++generation;
      challenge = null;
      loggingOut = true;
      try {
        await save(null);
      } catch {
        throw new ApiFailure(
          'Chưa thể xoá phiên trên thiết bị. Vui lòng thử đăng xuất lại.',
          'STORAGE_ERROR',
        );
      } finally {
        loggingOut = false;
      }
      session = null;
      changed(null);
      if (access) await request('/auth/logout', 'POST', undefined, access).catch(() => {});
    },
  };
}
