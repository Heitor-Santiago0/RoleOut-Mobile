import test from 'node:test';
import assert from 'node:assert/strict';
import { itineraryName, localDeparture, organizeSchedule, validatePlan } from '../src/itinerary.ts';

const now = new Date(2026, 9, 13, 23, 58);
const stops = [{ role: { id: 1, cidade: 'Uberaba', data: null, valor: 12.35 }, minutes: 60 }];
test('optional outing names are trimmed, bounded and retain the automatic fallback', () => {
  assert.equal(organizeSchedule(stops, '14/10/2026', '10:00', now, true, '  Aniversário da Ana  ').nome, 'Aniversário da Ana');
  assert.equal(organizeSchedule(stops, '14/10/2026', '10:00', now, true, '   ').nome, 'Rolê em Uberaba — 14/10/2026');
  assert.equal(itineraryName('Uberaba', '14/10/2026', 'a'.repeat(200)).length, 200);
  assert.throws(() => organizeSchedule(stops, '14/10/2026', '10:00', now, true, 'a'.repeat(201)), /200/);
  assert.match(validatePlan({ name: 'a'.repeat(201), city: 'Uberaba', date: '14/10/2026', start: '10:00', hours: '2' }, 1), /200/);
});
test('go now saves the device local date/time and existing stop order', () => {
  const departure = localDeparture(now);
  assert.deepEqual(departure, { date: '13/10/2026', time: '23:58' });
  const result = organizeSchedule(stops, departure.date, departure.time, now, false);
  assert.equal(result.data, '2026-10-13');
  assert.equal(result.orcamentoCentavos, 1235);
  assert.equal(result.pessoas, 1);
  assert.deepEqual(result.paradas, [{ roleId: 1, minutos: 60, horario: null }]);
});
test('scheduled outings require a real future date and valid time', () => {
  for (const [date, time] of [['31/02/2027', '10:00'], ['14/10/2026', '25:00'], ['13/10/2026', '23:57']]) {
    assert.throws(() => organizeSchedule(stops, date, time, now, true));
  }
  assert.equal(organizeSchedule(stops, '14/10/2026', '00:10', now, true).partida, '00:10');
});
test('saving does not bypass fixed event dates, cities or duration limits', () => {
  assert.throws(() => organizeSchedule([], '14/10/2026', '10:00', now, true));
  assert.throws(() => organizeSchedule([{ ...stops[0], role: { ...stops[0].role, data: '2026-10-15' } }], '14/10/2026', '10:00', now, true), /data/);
  assert.throws(() => organizeSchedule([...stops, { role: { ...stops[0].role, id: 2, cidade: 'Outra cidade' }, minutes: 60 }], '14/10/2026', '10:00', now, true), /cidade/);
  assert.throws(() => organizeSchedule([{ ...stops[0], minutes: 735 }], '14/10/2026', '10:00', now, true), /12 horas/);
});
