import test from 'node:test';
import assert from 'node:assert/strict';
import { cartLines, lineKey, stepQuantity } from '../src/domain/cart.ts';
import { acceptDraftPrice, applyDraftAck, type OrderDraft } from '../src/domain/draft.ts';
import type { Offering, SalesUser } from '../src/domain/contracts.ts';
const offers: Offering[] = [
  {
    skuId: 1,
    code: 'SKU-1',
    name: 'Milk',
    category: 'DRINK',
    uom: 'CHAI',
    factor: 1,
    price: 10,
    multiple: 1,
    minOrder: 1,
    baseUnit: 'CHAI',
  },
  {
    skuId: 1,
    code: 'SKU-1',
    name: 'Milk',
    category: 'DRINK',
    uom: 'THUNG',
    factor: 24,
    price: 240,
    multiple: 1,
    minOrder: 1,
    baseUnit: 'CHAI',
  },
];
test('Cart keeps two UOMs of one SKU separate and does not mutate inventory or prices', () => {
  assert.notEqual(lineKey(offers[0]), lineKey(offers[1]));
  assert.deepEqual(cartLines({ '1:CHAI': 2, '1:THUNG': 1 }, offers), [
    { skuId: 1, uom: 'CHAI', qty: 2 },
    { skuId: 1, uom: 'THUNG', qty: 1 },
  ]);
  assert.equal(offers[1].price, 240);
});
test('Cart refuses non-finite quantities, invalid multiples and below-minimum base amounts', () => {
  assert.throws(() => cartLines({ '1:CHAI': Infinity }, offers));
  assert.throws(() => cartLines({ '1:CHAI': 1.5 }, offers));
  assert.throws(() => cartLines({ '1:CHAI': 1 }, [{ ...offers[0], minOrder: 2 }]));
  assert.throws(() => cartLines({ '99:CHAI': 1 }, offers));
  assert.equal(stepQuantity(0, 1, -1), 0);
  assert.equal(stepQuantity(0.2, 0.1, 1), 0.3);
});
test('Explicit reprice keeps ownership, order identity, quantity and original creation time', () => {
  const pending = { ...draft, serverStatus: 'PRICE_CHANGED', status: 'NEEDS_REVIEW' as const };
  const quote = {
    lines: [{ skuId: 1, uom: 'CHAI', qty: 2, price: 12, discount: 0, total: 24, factor: 1 }],
    subtotal: 24,
    discount: 0,
    total: 24,
    benefits: [],
  };
  const result = acceptDraftPrice(pending, quote, user);
  assert.equal(result.payload.idempotencyKey, draft.payload.idempotencyKey);
  assert.equal(result.payload.createdAt, draft.payload.createdAt);
  assert.equal(result.payload.lines[0].qty, 2);
  assert.equal(result.payload.lines[0].clientPrice, 12);
  assert.equal(result.total, 24);
  assert.throws(() => acceptDraftPrice(draft, quote, user));
  assert.throws(() => acceptDraftPrice(pending, { ...quote, lines: [] }, user));
});
const user: SalesUser = {
  id: 3,
  name: 'Sales',
  role: 'SALES',
  nppId: 2,
  workspaceKey: 'npp:2',
  authorities: [],
};
const draft: OrderDraft = {
  id: 'draft-1',
  ownerId: 3,
  workspaceKey: 'npp:2',
  storeName: 'Store',
  status: 'LOCAL',
  total: 20,
  payload: {
    clientId: 'draft-1',
    idempotencyKey: 'stable-key-1',
    storeId: 1,
    createdAt: '2026-11-11T00:00:00Z',
    lines: [{ skuId: 1, uom: 'CHAI', qty: 2, clientPrice: 10 }],
  },
};
test('Only an owned successful ACK retires a draft; price changes and missing replies keep payload/key', () => {
  const review = applyDraftAck(
    draft,
    [{ clientId: 'draft-1', status: 'PRICE_CHANGED', message: 'Review price' }],
    user,
  );
  assert.equal(review.status, 'NEEDS_REVIEW');
  assert.equal(review.payload, draft.payload);
  assert.equal(applyDraftAck(draft, [], user).status, 'NEEDS_REVIEW');
  assert.equal(
    applyDraftAck(draft, [{ clientId: 'draft-1', status: 'CREATED' }], user).status,
    'NEEDS_REVIEW',
  );
  const sent = applyDraftAck(
    review,
    [{ clientId: 'draft-1', status: 'DUPLICATE', orderId: 14 }],
    user,
  );
  assert.equal(sent.orderId, 14);
  assert.equal(sent.status, 'ACKNOWLEDGED');
  assert.equal(sent.payload.idempotencyKey, 'stable-key-1');
  assert.throws(() => applyDraftAck(draft, [], { ...user, id: 4 }));
  assert.throws(() => applyDraftAck(draft, [], { ...user, workspaceKey: 'npp:9' }));
});
