import test from 'node:test';
import assert from 'node:assert/strict';
import { smartOrder } from '../src/smartOrder.ts';
import { additionProblem, formatTimeInput } from '../src/itinerary.ts';
const stop = (id, longitude, extra = {}) => ({ role: { id, nome: String(id), cidade: 'Uberaba', latitude: 0, longitude, ...extra }, minutes: 60 });

test('ordering rejects visits that exceed the published end without changing the stops', () => {
  const stops = [stop(1, 1, { horario: '10:00', horarioFim: '10:30' })];
  assert.throws(() => smartOrder(stops, '09:00'), /término/);
  assert.equal(stops[0].minutes, 60);
  assert.equal(smartOrder([{ ...stops[0], minutes: 30 }], '09:00').length, 1);
});
test('proximity starts from the supplied position and preserves every stop and duration', () => {
  const input = [stop(1, 3), stop(2, 1), stop(3, 2)];
  assert.deepEqual(smartOrder(input, '10:00', { latitude: 0, longitude: 0 }).map(s => s.role.id), [2, 3, 1]);
  assert.deepEqual(input.map(s => s.role.id), [1, 2, 3]);
});
test('urgent distant event takes precedence but a later event leaves room for a nearby visit', () => {
  const near = stop(1, 1); const event = stop(2, 3, { horario: '10:30' });
  assert.deepEqual(smartOrder([near, event], '10:00').map(s => s.role.id), [2, 1]);
  assert.deepEqual(smartOrder([near, stop(2, 3, { horario: '13:00' })], '10:00').map(s => s.role.id), [1, 2]);
});
test('user visit time is respected and cannot silently postpone a published event', () => {
  const near = stop(1, 1); const chosen = { ...stop(2, 3), horario: '10:30' };
  assert.equal(smartOrder([near, chosen], '10:00')[0].role.id, 2);
  assert.equal(smartOrder([near, { ...chosen, role: { ...chosen.role, horario: '10:15' } }], '10:00')[0].role.id, 2);
});
test('missing coordinates, invalid time and conflicting commitments never produce a misleading order', () => {
  assert.throws(() => smartOrder([stop(1, null)], '10:00'), /coordenadas/);
  assert.throws(() => smartOrder([{ ...stop(1, 0), horario: '25:10' }], '10:00'), /HH:MM/);
  assert.throws(() => smartOrder([stop(1, 0, { horario: '10:00' }), stop(2, 1, { horario: '10:30' })], '10:00'), /conflita/);
});
test('different dates cannot enter the same draft and time entry inserts its separator', () => {
  const selected = [{ role: { cidade: 'Uberaba', data: '2026-10-13' } }];
  assert.match(additionProblem(selected, { cidade: 'Uberaba', data: '2027-10-13' }), /datas diferentes/);
  assert.match(additionProblem([], { cidade: 'Uberaba', data: '2027-10-13' }, '13/10/2026'), /datas diferentes/);
  assert.equal(additionProblem([], { cidade: 'Uberaba', data: '2027-10-13' }, '13/10/2027'), '');
  assert.equal(formatTimeInput('1830'), '18:30');
  assert.equal(formatTimeInput('18:3'), '18:3');
});
