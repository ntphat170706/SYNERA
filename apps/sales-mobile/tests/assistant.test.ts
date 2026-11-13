import test from 'node:test';
import assert from 'node:assert/strict';
import { suggestOrder } from '../src/domain/orderAssistant.ts';
import type { Offering } from '../src/domain/contracts.ts';
const product = (skuId: number, name: string, uom: string, factor = 1): Offering => ({
  skuId,
  name,
  uom,
  factor,
  code: `SKU-${skuId}`,
  price: 10,
  category: 'DRINK',
  multiple: 1,
  minOrder: 1,
  baseUnit: 'CHAI',
});
const catalog = [
  product(1, 'Sữa tươi nguyên chất', 'CHAI'),
  product(1, 'Sữa tươi nguyên chất', 'THUNG', 24),
  product(2, 'Trà xanh thanh mát', 'LOC', 6),
  product(2, 'Trà xanh thanh mát', 'CHAI'),
  product(3, 'Sữa hạt hạnh nhân', 'CHAI'),
];
test('Assistant maps Vietnamese request to real SKU and UOM, never includes price or submits an order', () => {
  const result = suggestOrder('Cho tôi 2 thùng sữa tươi và 3 lốc trà xanh', catalog);
  assert.deepEqual(
    result.lines.map(({ skuId, uom, qty }) => ({ skuId, uom, qty })),
    [
      { skuId: 1, uom: 'THUNG', qty: 2 },
      { skuId: 2, uom: 'LOC', qty: 3 },
    ],
  );
  assert.deepEqual(result.unmatched, []);
  assert.equal('price' in result.lines[0], false);
});
test('Assistant handles number words/newlines and combines identical SKU+UOM only', () => {
  const result = suggestOrder('hai thùng sữa tươi\n1 thùng sữa tươi; 2 chai sữa tươi', catalog);
  assert.deepEqual(
    result.lines.map(({ uom, qty }) => ({ uom, qty })),
    [
      { uom: 'THUNG', qty: 3 },
      { uom: 'CHAI', qty: 2 },
    ],
  );
});
test('Ambiguous products, unsupported units, unknown names and invalid quantities stay unresolved', () => {
  for (const request of [
    '2 chai sữa',
    '1 thùng trà xanh',
    '4 chai nước táo',
    'sữa tươi',
    '0 chai sữa tươi',
    '-2 chai sữa tươi',
    '1.5 chai sữa tươi',
    '99999999999999999999999 chai sữa tươi',
  ]) {
    const result = suggestOrder(request, catalog);
    assert.equal(result.lines.length, 0, request);
    assert.equal(result.unmatched.length, 1, request);
  }
});
