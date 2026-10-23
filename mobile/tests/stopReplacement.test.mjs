import test from 'node:test';
import assert from 'node:assert/strict';
import { replaceStop, replacementProblem } from '../src/itinerary.ts';

const role = { id: 1, nome: 'Museu', cidade: 'Uberaba', categoria: 'Cultura', data: null, valor: 10 };
const alternative = { ...role, id: 3, nome: 'Teatro', valor: 25 };
const stops = [{ role, minutes: 45 }, { role: { ...role, id: 2 }, minutes: 90 }];
const plan = { city: 'Uberaba', date: '15/10/2026' };

test('replacement preserves slot, duration and other stops without mutating the original', () => {
  const next = replaceStop(stops, 1, alternative, plan);
  assert.deepEqual(next.map(stop => [stop.role.id, stop.minutes]), [[3, 45], [2, 90]]);
  assert.equal(next[1], stops[1]);
  assert.equal(stops[0].role.id, 1);
  assert.equal(next[0].role.valor, 25);
});

test('replacement rejects missing targets, duplicates, other cities/categories and invalid prices', () => {
  assert.throws(() => replaceStop(stops, 99, alternative, plan), /não está mais/);
  assert.throws(() => replaceStop(stops, 1, stops[1].role, plan), /já está/);
  for (const changes of [{ cidade: 'São Paulo' }, { categoria: 'Música' }, { valor: -1 }, { valor: NaN }]) {
    assert.notEqual(replacementProblem(stops, 1, { ...alternative, ...changes }, plan), '');
  }
  assert.equal(replacementProblem(stops, 1, { ...alternative, cidade: ' UBERABA ', categoria: 'cultura' }, plan), '');
});

test('replacement respects fixed event dates while supporting an unscheduled draft', () => {
  assert.equal(replacementProblem(stops, 1, { ...alternative, data: '2026-10-15' }, plan), '');
  assert.match(replacementProblem(stops, 1, { ...alternative, data: '2026-10-16' }, plan), /outra data/);
  assert.equal(replacementProblem(stops, 1, { ...alternative, data: '2026-10-16' }, { ...plan, date: '' }), '');
});
