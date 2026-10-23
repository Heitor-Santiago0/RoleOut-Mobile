import test from 'node:test';
import assert from 'node:assert/strict';
import { additionProblem, candidates, organizeSchedule, schedule, timingCheck } from '../src/itinerary.ts';
import { smartOrder } from '../src/smartOrder.ts';
import { nearbyHighlights } from '../src/recommendations.ts';

const event = { id: 1, nome: 'Festival', cidade: 'Uberaba', categoria: 'Cultura', valor: 0, data: '2027-12-31', dataFim: '2028-01-02', horario: '22:00', horarioFim: '02:00', latitude: -19.7, longitude: -47.9 };
test('continuous events remain eligible on intermediate dates and respect both boundaries', () => {
  assert.equal(candidates([event], ['Cultura'], '2028-01-01', 1, 0).length, 1);
  assert.equal(candidates([event], ['Cultura'], '2028-01-03', 1, 0).length, 0);
  assert.equal(candidates([event], ['Cultura'], '2027-12-30', 1, 0).length, 0);
  assert.equal(additionProblem([{ role: event, minutes: 60 }], { ...event, id: 2, data: '2028-01-01', dataFim: null }), '');
  assert.match(additionProblem([{ role: event, minutes: 60 }], { ...event, id: 2, data: '2028-01-03', dataFim: null }), /datas diferentes/);
});
test('overnight saves account for the end date and retain the next-day timeline', () => {
  const role = { ...event, dataFim: '2028-01-01' };
  const stops = [{ role, minutes: 240 }];
  const saved = organizeSchedule(stops, '31/12/2027', '22:00', new Date(2026, 9, 16), true);
  assert.equal(saved.duracaoMinutos, 240);
  assert.equal(schedule(stops, '22:00', '2027-12-31')[0].to, '02:00 (+1 dia)');
  assert.match(timingCheck([{ role, minutes: 255 }], '22:00', '2027-12-31').problem, /término/);
});
test('later days do not repeat the original opening time and still honor closing time', () => {
  const stops = [{ role: event, minutes: 60, horario: '01:00' }];
  assert.deepEqual(timingCheck(stops, '01:00', '2028-01-02'), { minutes: 60, problem: '' });
  assert.equal(schedule(stops, '01:00', '2028-01-02')[0].to, '02:00');
  assert.deepEqual(smartOrder(stops, '01:00', undefined, '2028-01-02'), stops);
  assert.throws(() => smartOrder([{ role: event, minutes: 75, horario: '01:00' }], '01:00', undefined, '2028-01-02'), /término/);
  assert.throws(() => organizeSchedule(stops, '03/01/2028', '01:00', new Date(2026, 9, 16), true), /data/);
});
test('nearby suggestions retain ongoing multi-day events, without reviving expired ones', () => {
  const preferences = { status: 'CONCLUIDO', atividades: ['Museus'], musica: [], comida: [], ambientes: [] };
  const items = [{ ...event, distanciaKm: 1 }];
  assert.equal(nearbyHighlights(items, preferences, '2028-01-01').length, 1);
  assert.equal(nearbyHighlights(items, preferences, '2028-01-03').length, 0);
});
