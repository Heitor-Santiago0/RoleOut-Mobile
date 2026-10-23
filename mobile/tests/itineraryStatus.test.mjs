import test from 'node:test';
import assert from 'node:assert/strict';
import { itineraryStatus } from '../src/itineraryStatus.ts';
const now = new Date(2026, 9, 13, 18, 30);
const item = { data: '2026-10-13', partida: '18:30', totalParadas: 2, paradasConcluidas: 0 };
test('status uses local scheduled time without assuming a visit happened', () => {
  assert.equal(itineraryStatus(item, now).label, 'Pronto para ir');
  assert.equal(itineraryStatus({ ...item, partida: '19:00' }, now).label, 'Agendado');
  assert.equal(itineraryStatus({ ...item, data: '2026-10-14' }, now).label, 'Agendado');
  assert.equal(itineraryStatus({ ...item, data: '2026-10-12' }, now).label, 'Pendente');
});
test('actual progress takes precedence and undo restores pending status', () => {
  assert.equal(itineraryStatus({ ...item, paradasConcluidas: 1 }, now).label, 'Em andamento');
  assert.equal(itineraryStatus({ ...item, paradasConcluidas: 2 }, now).label, 'Concluído');
  assert.notEqual(itineraryStatus({ ...item, totalParadas: 0 }, now).label, 'Concluído');
  assert.equal(itineraryStatus({ ...item, paradasConcluidas: 0 }, now).label, 'Pronto para ir');
});
