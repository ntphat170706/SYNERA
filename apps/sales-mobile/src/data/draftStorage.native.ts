import * as SQLite from 'expo-sqlite';
import type { OrderDraft } from '../domain/draft';
const database = SQLite.openDatabaseAsync('synera-sales-orders.db').then(async (db) => {
  await db.execAsync(
    'CREATE TABLE IF NOT EXISTS sales_order_draft (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)',
  );
  return db;
});
export async function readDrafts(): Promise<OrderDraft[]> {
  const db = await database;
  return (await db.getAllAsync<{ payload: string }>('SELECT payload FROM sales_order_draft')).map(
    (row) => JSON.parse(row.payload),
  );
}
export async function saveDraft(draft: OrderDraft) {
  const db = await database;
  await db.runAsync(
    'INSERT INTO sales_order_draft(id,payload) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload',
    draft.id,
    JSON.stringify(draft),
  );
}
