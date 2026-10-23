import test from 'node:test';
import assert from 'node:assert/strict';
import { featuredItinerary, groupItineraries, itineraryGroup, loadItineraryPages, searchItineraries } from '../src/itineraryGroups.ts';
const now = new Date(2026, 9, 13, 23, 30);
const item = (id, data, partida = '10:00', paradasConcluidas = 0) => ({ id, data, partida, totalParadas: 2, paradasConcluidas });
test('search matches name and city without accents while preserving completion filters', () => {
  const items = [
    { ...item(1, '2026-10-13'), nome: 'Aniversário da Ana', cidade: 'São Paulo' },
    { ...item(2, '2026-10-13', '10:00', 2), nome: 'Aniversário da Ana', cidade: 'Uberaba' },
  ];
  assert.deepEqual(searchItineraries(items, '  ANIVERSARIO sao  ').map(value => value.id), [1]);
  assert.equal(searchItineraries(items, 'ubERaba')[0].id, 2);
  assert.equal(searchItineraries(items, 'praia').length, 0);
  assert.deepEqual(searchItineraries(items, '  '), items);
  assert.equal(groupItineraries(searchItineraries(items, 'ana'), now, 'completed')[0].items[0].id, 2);
});
test('Explore prioritizes ongoing, today and the nearest upcoming plan', () => {
  const data = [item(1, '2027-10-13'), item(2, '2026-10-14'), item(3, '2026-10-13'), item(4, '2026-10-12', '10:00', 1)];
  assert.equal(featuredItinerary(data, now).id, 4);
  assert.equal(featuredItinerary(data.slice(0, 3), now).id, 3);
  assert.equal(featuredItinerary(data.slice(0, 2), now).id, 2);
});
test('Explore stops featuring completed plans and does not treat past pending plans as upcoming', () => {
  const ongoing = item(1, '2026-10-12', '10:00', 1);
  assert.equal(featuredItinerary([ongoing], now).id, 1);
  assert.equal(featuredItinerary([{ ...ongoing, paradasConcluidas: 2 }], now), null);
  assert.equal(featuredItinerary([item(2, '2026-10-12')], now), null);
  assert.equal(featuredItinerary([], now), null);
});

test('today uses the device calendar and past dates never imply completion', () => {
  assert.equal(itineraryGroup(item(1, '2026-10-13'), now), 'today');
  assert.equal(itineraryGroup(item(2, '2026-10-12'), now), 'pending');
  assert.equal(itineraryGroup(item(3, '2026-10-14'), now), 'scheduled');
  assert.equal(itineraryGroup(item(4, '2026-10-12', '10:00', 1), now), 'ongoing');
  assert.equal(itineraryGroup(item(5, '2026-10-12', '10:00', 2), now), 'completed');
});
test('upcoming plans sort by departure rather than creation and filters do not mutate input', () => {
  const items = [item(6, '2027-10-13'), item(5, '2026-10-15'), item(4, '2026-10-14', '18:00'), item(3, '2026-10-14', '09:00'), item(2, '2026-10-12', '10:00', 2), item(1, '2026-10-13')];
  const groups = groupItineraries(items, now, 'active');
  assert.deepEqual(groups.map(group => group.key), ['today', 'scheduled']);
  assert.deepEqual(groups[1].items.map(item => item.id), [3, 4, 5, 6]);
  assert.deepEqual(groupItineraries(items, now, 'completed')[0].items.map(item => item.id), [2]);
  assert.equal(items[0].id, 6);
});
test('loading includes later pages before filtering, removes duplicates and stops at the short page', async () => {
  const offsets = [];
  const result = await loadItineraryPages(async offset => {
    offsets.push(offset);
    return offset === 0 ? Array.from({ length: 20 }, (_, id) => item(id, '2026-10-12', '10:00', 2)) : [item(19, '2026-10-12', '10:00', 2), item(20, '2026-10-14')];
  });
  assert.deepEqual(offsets, [0, 20]);
  assert.equal(result.length, 21);
  assert.equal(groupItineraries(result, now, 'active')[0].items[0].id, 20);
});
test('a failed later page does not return a misleading partial overview', async () => {
  await assert.rejects(loadItineraryPages(async offset => {
    if (offset) throw new Error('offline');
    return Array.from({ length: 20 }, (_, id) => item(id, '2026-10-13'));
  }), /offline/);
});
