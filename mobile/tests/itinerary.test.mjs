import test from 'node:test';
import assert from 'node:assert/strict';
import { candidates, isoDate, moneyCents, planProblem, schedule, totalCents, validatePlan } from '../src/itinerary.ts';
const role = (id, valor) => ({ id, valor });
test('candidates respect date, group budget and categories without inventing availability', () => {
  const roles = [
    { ...role(1, 20), categoria: 'cultura', data: '2026-10-15' },
    { ...role(2, 0), categoria: 'Cultura', data: null },
    { ...role(3, 5), categoria: 'Cultura', data: '2026-10-16' },
    { ...role(4, 1), categoria: 'Música', data: null },
    { ...role(5, 30), categoria: 'Cultura', data: null },
  ];
  assert.deepEqual(candidates(roles, ['Cultura'], '2026-10-15', 2, 4000).map(value => value.id), [2, 1]);
});
test('dates reject nonexistent days and accept leap years', () => {
  assert.equal(isoDate('31/02/2026'), null);
  assert.equal(isoDate('29/02/2028'), '2028-02-29');
  assert.equal(isoDate('29/02/2026'), null);
});
test('money uses cents and rejects ambiguous or negative amounts', () => {
  assert.equal(moneyCents('12,30'), 1230);
  assert.equal(moneyCents('0'), 0);
  for (const value of ['', '-1', '1e2', '1.234,56', '0.001']) assert.equal(moneyCents(value), null);
});
test('group costs obey budget, reject duplicate stops and respect time', () => {
  const stops = [{ role: role(1, 0.1), minutes: 60 }, { role: role(2, 0.2), minutes: 60 }];
  assert.equal(totalCents(stops, 3), 90);
  assert.equal(planProblem(stops, 3, 90, 120), '');
  assert.match(planProblem(stops, 3, 89, 120), /orçamento/);
  assert.match(planProblem(stops, 3, 90, 119), /duração/);
  assert.match(planProblem([stops[0], stops[0]], 1, 100, 120), /já está/);
});
test('ordering and duration determine times across midnight', () => {
  const result = schedule([{ role: role(2, 10), minutes: 60 }, { role: role(1, 20), minutes: 30 }], '23:30');
  assert.equal(result[0].role.id, 2);
  assert.equal(result[0].to, '00:30 (+1 dia)');
  assert.equal(result[1].from, result[0].to);
  assert.equal(result[1].to, '01:00 (+1 dia)');
});
test('planning fields enforce valid departure, people and duration', () => {
  const plan = { city: 'Uberaba', date: '15/10/2026', start: '10:00', hours: '4', people: '2', budget: '100' };
  assert.equal(validatePlan(plan, 1), ''); assert.equal(validatePlan(plan, 2), '');
  assert.match(validatePlan({ ...plan, start: '24:00' }, 1), /partida/);
  assert.match(validatePlan({ ...plan, hours: '0' }, 1), /duração/);
  assert.match(validatePlan({ ...plan, people: '0' }, 2), /pessoas/);
});
