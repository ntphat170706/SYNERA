import Dexie, { type Table } from 'dexie';
import type { Order } from './types';
import type { SessionUser } from './api';
export interface OfflineDraft extends Order {
  ownerId?: number;
  workspaceKey?: string;
  serverPayload?: Record<string, unknown>;
  syncError?: string;
  serverPrice?: unknown;
}
interface CachedRead {
  key: string;
  value: unknown;
  savedAt: number;
}
interface CachedIdentity {
  key: string;
  user: SessionUser;
  savedAt: number;
}
class OfflineDB extends Dexie {
  drafts!: Table<OfflineDraft, string>;
  reads!: Table<CachedRead, string>;
  identity!: Table<CachedIdentity, string>;
  constructor() {
    super('synera-offline');
    this.version(1).stores({ drafts: 'id,idempotencyKey,status' });
    this.version(2).stores({ drafts: 'id,idempotencyKey,status,ownerId' });
    this.version(3).stores({ drafts: 'id,idempotencyKey,status,ownerId,workspaceKey' });
    this.version(4).stores({
      drafts: 'id,idempotencyKey,status,ownerId,workspaceKey',
      reads: 'key,savedAt',
      identity: 'key',
    });
  }
}
export const offlineDB = new OfflineDB();
const TTL = 24 * 60 * 60 * 1000;
let identity: SessionUser | null = null;
export async function rememberIdentity(user: SessionUser | null) {
  identity = user;
  if (user?.role === 'SALES')
    await offlineDB.identity.put({ key: 'last-sales', user, savedAt: Date.now() });
  else
    await offlineDB.transaction('rw', offlineDB.identity, offlineDB.reads, async () => {
      await offlineDB.identity.delete('last-sales');
      await offlineDB.reads.clear();
    });
}
export async function offlineIdentity() {
  const saved = await offlineDB.identity.get('last-sales');
  if (!saved || Date.now() - saved.savedAt > TTL) return null;
  identity = saved.user;
  return saved.user;
}
export function cacheKey(path: string) {
  // Only the references needed to draft an owned sales order are retained offline.
  if (
    identity?.role !== 'SALES' ||
    !/^\/(catalog\/(skus(?:\/\d+\/units)?|offerings)|stores|organizations|visits\/today)(\?|$)/.test(
      path,
    )
  )
    return null;
  return `${identity.id}:${identity.workspaceKey || ''}:${path}`;
}
export async function cachedRead<T>(key: string | null): Promise<T | undefined> {
  const cached = key ? await offlineDB.reads.get(key) : undefined;
  return cached && Date.now() - cached.savedAt <= TTL ? (cached.value as T) : undefined;
}
export async function cacheRead(key: string | null, value: unknown) {
  if (key) await offlineDB.reads.put({ key, value, savedAt: Date.now() });
}
