import { cacheKey, cachedRead, cacheRead, rememberIdentity } from './offline';
export type RecordData = Record<string, unknown>;
export interface SessionUser {
  id: number;
  name: string;
  role: string;
  nppId: number;
  authorities: string[];
  roleCode?: string;
  workspaceKey?: string;
}
export interface SessionResult {
  accessToken?: string;
  user?: SessionUser;
  requiresOtp?: boolean;
  challengeId?: number;
  localOtp?: string;
}
export class ApiError extends Error {
  constructor(
    message: string,
    public code = 'REQUEST_FAILED',
    public status = 0,
    public details: unknown = {},
  ) {
    super(message);
  }
}
let accessToken = '';
let workspaceKey = '';
let actorId = 0;
const pendingCommands = new Map<string, { key: string; promise?: Promise<unknown> }>();
let refreshPending: Promise<SessionResult | null> | null = null;
let onExpired = () => {};
let onUpdated = (_user: SessionUser) => {};
function logoutPreference(value?: boolean) {
  try {
    if (value === undefined) return localStorage.getItem('synera:logout-pending') === 'true';
    if (value) localStorage.setItem('synera:logout-pending', 'true');
    else localStorage.removeItem('synera:logout-pending');
  } catch {
    /* Storage may be unavailable in a private browser. */
  }
  return false;
}
export async function forgetSession() {
  accessToken = '';
  workspaceKey = '';
  actorId = 0;
  await rememberIdentity(null).catch(() => {});
}
export function sessionListener(
  listener: () => void,
  updated: (user: SessionUser) => void = () => {},
) {
  onExpired = listener;
  onUpdated = updated;
  return () => {
    onExpired = () => {};
    onUpdated = () => {};
  };
}
export function acceptSession(result: SessionResult) {
  accessToken = result.accessToken || '';
  workspaceKey = result.user?.workspaceKey || '';
  actorId = result.user?.id || 0;
  if (result.user) onUpdated(result.user);
  if (result.user) void rememberIdentity(result.user).catch(() => {});
  if (result.user) logoutPreference(false);
}
export async function browserSession(action: string, body?: unknown): Promise<SessionResult> {
  if (action === 'logout') logoutPreference(true);
  const result = await fetch('/api/v1/auth/session/' + action, {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      'X-Session-Request': 'synera',
      ...(accessToken && action === 'workspace' ? { Authorization: 'Bearer ' + accessToken } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const envelope = await result.json();
  if (!result.ok || !envelope.success)
    throw new ApiError(
      envelope.error?.message || 'Không thể xác thực phiên.',
      envelope.error?.code,
      result.status,
    );
  const data = envelope.data as SessionResult;
  if (data.accessToken) acceptSession(data);
  if (action === 'logout') {
    await forgetSession();
    logoutPreference(false);
  }
  return data;
}
export function restoreSession(): Promise<SessionResult | null> {
  if (logoutPreference())
    return browserSession('logout')
      .then(() => null)
      .catch(() => null);
  if (!refreshPending)
    refreshPending = browserSession('refresh')
      .catch(async (e: unknown) => {
        if (e instanceof ApiError && e.status === 401) {
          await forgetSession();
          return null;
        }
        throw e;
      })
      .finally(() => {
        refreshPending = null;
      });
  return refreshPending;
}
export async function api<T = RecordData>(
  path: string,
  options: {
    method?: string;
    body?: unknown;
    key?: string;
    signal?: AbortSignal;
    raw?: boolean;
  } = {},
  retry = true,
): Promise<T> {
  const originalWorkspace = workspaceKey;
  const method = options.method || (options.body === undefined ? 'GET' : 'POST');
  const referenceKey = method === 'GET' && !options.raw ? cacheKey(path) : null;
  if (!navigator.onLine && referenceKey) {
    const cached = await cachedRead<T>(referenceKey);
    if (cached !== undefined) return cached;
    throw new ApiError(
      'Dữ liệu này chưa được tải trên thiết bị. Kết nối mạng để cập nhật.',
      'OFFLINE_REFERENCE_MISSING',
    );
  }
  const headers: Record<string, string> = { Accept: options.raw ? '*/*' : 'application/json' };
  if (accessToken) headers.Authorization = 'Bearer ' + accessToken;
  const multipart = options.body instanceof FormData;
  if (options.body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
  if (options.key) headers['Idempotency-Key'] = options.key;
  let response: Response;
  try {
    response = await fetch('/api/v1' + path, {
      method,
      headers,
      credentials: 'same-origin',
      signal: options.signal,
      body:
        options.body === undefined
          ? undefined
          : multipart
            ? (options.body as FormData)
            : JSON.stringify(options.body),
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError('Không kết nối được máy chủ. Kiểm tra mạng và thử lại.', 'NETWORK_ERROR');
  }
  if (response.status === 401 && retry && accessToken) {
    const refreshed = await restoreSession();
    if (refreshed) {
      if (originalWorkspace !== workspaceKey)
        throw new ApiError(
          'Không gian làm việc đã đổi. Hãy kiểm tra và thực hiện lại.',
          'WORKSPACE_CHANGED',
          409,
        );
      return api(path, options, false);
    }
    onExpired();
  }
  if (options.raw && response.ok) return response.blob() as Promise<T>;
  const envelope = await response
    .json()
    .catch(() => ({ error: { message: 'Máy chủ trả dữ liệu không hợp lệ.' } }));
  if (!response.ok || !envelope.success)
    throw new ApiError(
      envelope.error?.message || 'Không thể thực hiện yêu cầu.',
      envelope.error?.code,
      response.status,
      envelope.error?.details,
    );
  if (referenceKey) void cacheRead(referenceKey, envelope.data).catch(() => {});
  return envelope.data as T;
}
export function command<T = RecordData>(path: string, body: unknown = {}, key?: string) {
  const fingerprint = JSON.stringify([actorId, workspaceKey, path, body]);
  const previous = pendingCommands.get(fingerprint);
  if (previous?.promise) return previous.promise as Promise<T>;
  const pending = previous || { key: key || crypto.randomUUID() };
  pendingCommands.set(fingerprint, pending);
  const promise = api<T>(path, { method: 'POST', body, key: pending.key })
    .then((value) => {
      pendingCommands.delete(fingerprint);
      window.dispatchEvent(new Event('synera:data-changed'));
      return value;
    })
    .catch((error: unknown) => {
      pending.promise = undefined;
      // Retain the same key when the response is unknown. A retry must not create a second document.
      if (error instanceof ApiError && error.status >= 400) pendingCommands.delete(fingerprint);
      throw error;
    });
  pending.promise = promise;
  return promise;
}
export const textValue = (value: unknown) => (value == null ? '' : String(value));
export const numberValue = (value: unknown) => Number(value || 0);
export const records = (value: unknown): RecordData[] =>
  Array.isArray(value) ? (value as RecordData[]) : [];
export const record = (value: unknown): RecordData =>
  value && typeof value === 'object' && !Array.isArray(value) ? (value as RecordData) : {};
export async function download(path: string, filename: string) {
  const blob = await api<Blob>(path, { raw: true });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
