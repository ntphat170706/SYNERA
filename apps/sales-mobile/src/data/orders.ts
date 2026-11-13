import { salesClient } from '../auth/session';
import { applyDraftAck, ownsDraft, type OrderDraft } from '../domain/draft';
import type { CommandAck, SalesUser } from '../domain/contracts';
import { saveDraft, readDrafts } from './draftStorage';
const pending = new Map<string, Promise<OrderDraft>>();
export { saveDraft };
export async function ownDrafts(user: SalesUser) {
  return (await readDrafts())
    .filter((d) => ownsDraft(d, user) && d.status !== 'ACKNOWLEDGED')
    .sort((a, b) => a.payload.createdAt.localeCompare(b.payload.createdAt));
}
export function sendDraft(draft: OrderDraft, user: SalesUser): Promise<OrderDraft> {
  if (!ownsDraft(draft, user)) return Promise.reject(Error('Nháp không thuộc phiên hiện tại.'));
  const actor = salesClient.user();
  if (!actor || actor.id !== user.id || actor.workspaceKey !== user.workspaceKey)
    return Promise.reject(Error('Phiên đã thay đổi. Vui lòng mở lại danh sách nháp.'));
  if (draft.status === 'ACKNOWLEDGED') return Promise.resolve(draft);
  const existing = pending.get(draft.id);
  if (existing) return existing;
  const task = (async () => {
    await saveDraft(draft);
    try {
      const replies = await salesClient.post<(CommandAck & { message?: string })[]>(
        '/sales-orders/sync',
        [draft.payload],
        draft.payload.idempotencyKey,
      );
      const result = applyDraftAck(draft, replies, user);
      await saveDraft(result);
      return result;
    } catch (error) {
      const result: OrderDraft = {
        ...draft,
        status: 'NEEDS_REVIEW',
        message: error instanceof Error ? error.message : 'Chưa gửi được. Nháp vẫn được lưu.',
      };
      await saveDraft(result);
      return result;
    }
  })().finally(() => pending.delete(draft.id));
  pending.set(draft.id, task);
  return task;
}
