import test from 'node:test';
import assert from 'node:assert/strict';
import { elapsedVisit, greeting, periodRange } from '../src/domain/home.ts';
test('Visit clock uses persisted server check-in timestamp, handles resume, midnight, invalid and future clocks', () => {
  const start = '2026-11-11T16:55:00Z';
  assert.equal(elapsedVisit(start, Date.parse('2026-11-11T17:15:22Z')), '00:20:22');
  assert.equal(elapsedVisit(start, Date.parse('2026-11-11T18:15:22Z')), '01:20:22');
  assert.equal(elapsedVisit(start, Date.parse('2026-11-11T16:50:00Z')), '00:00:00');
  assert.equal(elapsedVisit('', Date.now()), null);
  assert.equal(elapsedVisit('invalid', Date.now()), null);
});
test('Greeting and period are determined in Vietnam rather than device or UTC date', () => {
  const midnight = Date.parse('2026-11-11T17:10:00Z');
  assert.deepEqual(periodRange('today', midnight), { from: '2026-11-12', to: '2026-11-12' });
  assert.deepEqual(periodRange('month', midnight), { from: '2026-11-01', to: '2026-11-12' });
  assert.equal(greeting(Date.parse('2026-11-12T01:00:00Z')), 'Chào buổi sáng');
  assert.equal(greeting(Date.parse('2026-11-12T07:00:00Z')), 'Chào buổi chiều');
});
