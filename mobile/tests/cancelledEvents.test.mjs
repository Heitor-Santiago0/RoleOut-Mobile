import test from 'node:test';
import assert from 'node:assert/strict';
import { additionProblem, replacementProblem, selectionProblem, candidates, organizeSchedule } from '../src/itinerary.ts';
import { preferenceReasons } from '../src/recommendations.ts';
import { directionsUrl } from '../src/directions.ts';
import { smartOrder } from '../src/smartOrder.ts';

const role = { id: 1, nome: 'Show', cidade: 'Uberaba', local: 'Centro', categoria: 'Cultura', data: null, valor: 0, cancelado: true, latitude: -19.75, longitude: -47.93 };
const plan = { city: 'Uberaba', date: '15/10/2027', start: '10:00', hours: '4', people: '1', budget: '0' };
test('known cancellations cannot be added, substituted or saved from a stale selection', () => {
  assert.match(additionProblem([], role), /cancelado/);
  assert.match(replacementProblem([{ role: { ...role, id: 2 }, minutes: 60 }], 2, role, plan), /cancelado/);
  assert.match(selectionProblem([{ role, minutes: 60 }], plan), /cancelado/);
  assert.throws(() => organizeSchedule([{ role, minutes: 60 }], plan.date, plan.start, new Date(2026, 9, 15), true), /cancelado/);
  assert.throws(() => smartOrder([{ role, minutes: 60 }], '10:00'), /cancelado/);
});
test('cancellations do not generate recommendations or directions; old active records remain supported', () => {
  const interests = { status: 'CONCLUIDO', musica: [], comida: [], atividades: ['Museus'], ambientes: [] };
  assert.deepEqual(preferenceReasons(role, interests, '2027-10-15'), []);
  assert.deepEqual(candidates([role], ['Cultura'], '2027-10-15', 1, 0), []);
  assert.equal(directionsUrl(role), null);
  const legacy = { ...role, cancelado: undefined };
  assert.equal(candidates([legacy], ['Cultura'], '2027-10-15', 1, 0).length, 1);
  assert.match(directionsUrl(legacy), /google.com/);
});
