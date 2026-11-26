import test from 'node:test';
import assert from 'node:assert/strict';
import { copyInput } from '../src/copyItinerary.ts';
const plan = { name: 'Minha versão', city: 'Uberaba', date: '26/11/2026', start: '10:00', hours: '3', people: '2', budget: '50,00', budgetMode: 'group' };
const stops = [{ roleId: 9, nome: 'Museu', minutos: 60 }];
test('copy sends only new planning, never an author override or original private settings', () => {
  const result = copyInput({ ...plan, autorId: 'fake' }, stops);
  assert.equal(result.planejamento.orcamentoCentavos, 5000); assert.equal(result.planejamento.data, '2026-11-26');
  assert.equal(result.planejamento.autorId, undefined); assert.deepEqual(result.planejamento.paradas, [{ roleId: 9, minutos: 60, horario: null }]);
  assert.equal(stops[0].minutos, 60);
});
test('copy rejects duplicate stops invalid dates durations and hours before request', () => {
  for (const value of [[], [...stops, ...stops], [{ ...stops[0], minutos: 10 }], [{ ...stops[0], horario: '99:00' }], [{ ...stops[0], minutos: 240 }]]) assert.throws(() => copyInput(plan, value));
  assert.throws(() => copyInput({ ...plan, date: '31/02/2027' }, stops));
  assert.throws(() => copyInput({ ...plan, budget: '-10' }, stops));
});
