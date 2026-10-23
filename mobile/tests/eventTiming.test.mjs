import test from 'node:test';
import assert from 'node:assert/strict';
import { timingCheck, organizeSchedule } from '../src/itinerary.ts';

const event = { id: 1, nome: 'Show', cidade: 'Uberaba', data: '2027-10-15', valor: 0, horario: '11:00', horarioFim: '12:00' };
test('a visit may end exactly at the published end, but not after it', () => {
  assert.deepEqual(timingCheck([{ role: event, minutes: 60 }], '10:00'), { minutes: 120, problem: '' });
  assert.match(timingCheck([{ role: event, minutes: 75 }], '10:00').problem, /término/);
  assert.match(timingCheck([{ role: event, minutes: 30 }], '11:01').problem, /horário marcado/);
});
test('saving includes waiting and does not invent an end for old events', () => {
  const now = new Date(2026, 9, 15);
  const result = organizeSchedule([{ role: event, minutes: 60 }], '15/10/2027', '09:00', now, true);
  assert.equal(result.duracaoMinutos, 180);
  assert.equal(timingCheck([{ role: { ...event, horarioFim: null }, minutes: 120 }], '11:00').problem, '');
  assert.throws(() => organizeSchedule([{ role: event, minutes: 75 }], '15/10/2027', '09:00', now, true), /término/);
  assert.throws(() => organizeSchedule([{ role: { ...event, horario: '23:00', horarioFim: null }, minutes: 60 }], '15/10/2027', '09:00', now, true), /12 horas/);
});
test('invalid visit times and conflicts between stops cannot be saved', () => {
  assert.match(timingCheck([{ role: event, minutes: 30, horario: '99:00' }], '10:00').problem, /horário/);
  const stops = [{ role: { ...event, id: 2, horario: null, horarioFim: null }, minutes: 90 }, { role: event, minutes: 30 }];
  assert.match(timingCheck(stops, '10:00').problem, /conflita/);
  assert.equal(stops[0].minutes, 90);
});
