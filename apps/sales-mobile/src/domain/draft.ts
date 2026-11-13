import type { CommandAck, PriceQuote, SalesUser } from './contracts.ts';
export interface OrderDraft {
  id: string;
  ownerId: number;
  workspaceKey: string;
  storeName: string;
  status: 'LOCAL' | 'NEEDS_REVIEW' | 'ACKNOWLEDGED';
  message?: string;
  serverStatus?: string;
  orderId?: number;
  total: number;
  payload: {
    clientId: string;
    idempotencyKey: string;
    storeId: number;
    createdAt: string;
    lines: { skuId: number; uom: string; qty: number; clientPrice: number }[];
  };
}
export function ownsDraft(draft: OrderDraft, user: SalesUser) {
  return draft.ownerId === user.id && draft.workspaceKey === user.workspaceKey;
}
export function applyDraftAck(
  draft: OrderDraft,
  replies: (CommandAck & { message?: string })[],
  user: SalesUser,
): OrderDraft {
  if (!ownsDraft(draft, user)) throw Error('Nháp thuộc tài khoản hoặc phạm vi khác.');
  const reply = replies.find((r) => r.clientId === draft.payload.clientId);
  if (!reply)
    return {
      ...draft,
      status: 'NEEDS_REVIEW',
      message: 'Chưa có xác nhận. Gửi lại cùng mã để kiểm tra.',
    };
  if (
    ['CREATED', 'DUPLICATE'].includes(reply.status) &&
    Number.isSafeInteger(reply.orderId) &&
    Number(reply.orderId) > 0
  )
    return { ...draft, status: 'ACKNOWLEDGED', orderId: reply.orderId, message: undefined };
  return {
    ...draft,
    status: 'NEEDS_REVIEW',
    serverStatus: reply.status,
    message: reply.message || 'Đơn chưa được xác nhận. Nháp vẫn được giữ.',
  };
}
export function acceptDraftPrice(
  draft: OrderDraft,
  quote: PriceQuote,
  user: SalesUser,
): OrderDraft {
  if (!ownsDraft(draft, user) || draft.serverStatus !== 'PRICE_CHANGED')
    throw Error('Nháp không ở trạng thái cần xác nhận giá.');
  const lines = draft.payload.lines.map((line) => {
    const price = quote.lines.find(
      (q) => q.skuId === line.skuId && q.uom === line.uom && q.qty === line.qty,
    )?.price;
    if (price === undefined || !Number.isFinite(price) || price < 0)
      throw Error('Báo giá không khớp dòng hàng.');
    return { ...line, clientPrice: price };
  });
  return {
    ...draft,
    total: quote.total,
    status: 'LOCAL',
    serverStatus: undefined,
    message: undefined,
    payload: { ...draft.payload, lines },
  };
}
