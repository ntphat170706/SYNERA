import type { OrderDraft } from '../domain/draft';
// Browser preview only. Metro selects .native.ts on iOS/Android (SQLite).
const key = 'synera.sales.order-drafts.v1';
export async function readDrafts(): Promise<OrderDraft[]> {
  const raw = localStorage.getItem(key);
  return raw ? JSON.parse(raw) : [];
}
export async function saveDraft(draft: OrderDraft) {
  const rows = await readDrafts(),
    index = rows.findIndex((row) => row.id === draft.id);
  if (index < 0) rows.push(draft);
  else rows[index] = draft;
  localStorage.setItem(key, JSON.stringify(rows));
}
