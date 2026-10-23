import test from 'node:test';
import assert from 'node:assert/strict';
import { additionProblem, appendStop, selectionProblem } from '../src/itinerary.ts';
import { directionsUrl } from '../src/directions.ts';

test('directions prefer precise coordinates including zero and reject invalid pairs', () => {
  const role = { local: 'Name', cidade: 'City', latitude: 0, longitude: -47.930001 };
  assert.equal(new URL(directionsUrl(role)).searchParams.get('destination'), '0,-47.930001');
  for (const pair of [{ latitude: null }, { longitude: undefined }, { latitude: 91 }, { longitude: Infinity }]) {
    assert.equal(new URL(directionsUrl({ ...role, ...pair })).searchParams.get('destination'), 'Name, City');
  }
});

test('adding from Explore preserves order and durations without duplicates', () => {
  const first = { id: 1, nome: 'Arte' };
  const stops = [{ role: first, minutes: 30 }];
  assert.equal(appendStop(stops, first), stops);
  const next = appendStop(stops, { id: 2, nome: 'Cafe' });
  assert.deepEqual(next.map(s => [s.role.id, s.minutes]), [[1, 30], [2, 60]]);
  assert.equal(stops.length, 1);
});
test('selected events must match the itinerary city and date', () => {
  const plan = { city: 'Uberaba', date: '15/10/2026' };
  const stops = [{ role: { cidade: ' uberaba ', data: null } }];
  assert.equal(selectionProblem(stops, plan), '');
  assert.match(selectionProblem([{ role: { cidade: 'Sao Paulo', data: null } }], plan), /cidade/);
  assert.match(selectionProblem([{ role: { cidade: 'Uberaba', data: '2026-10-16' } }], plan), /data/);
  assert.equal(selectionProblem([{ role: { cidade: 'Uberaba', data: '2026-10-15' } }], plan), '');
});
test('adding from details rejects another city including an already mixed draft', () => {
  const cafe = { id: 2, cidade: 'Uberaba' };
  const draft = { id: 1, cidade: 'Cidade Integracao' };
  assert.equal(additionProblem([], cafe), '');
  assert.equal(additionProblem([{ role: { cidade: ' UBERABA ' } }], cafe), '');
  assert.match(additionProblem([{ role: draft }], cafe), /outra cidade/);
  assert.match(additionProblem([{ role: cafe }, { role: draft }], cafe), /outra cidade/);
  const existing = [{ role: draft, minutes: 60 }];
  additionProblem(existing, cafe);
  assert.deepEqual(existing, [{ role: draft, minutes: 60 }]);
});
test('directions safely encode a named venue and never invent a missing destination', () => {
  assert.equal(directionsUrl({ local: null, cidade: 'Uberaba' }), null);
  assert.equal(directionsUrl({ local: 'Cafe', cidade: ' ' }), null);
  const url = new URL(directionsUrl({ local: 'Cafe & Arte #1', cidade: 'Uberaba' }));
  assert.equal(url.origin, 'https://www.google.com');
  assert.equal(url.searchParams.get('api'), '1');
  assert.equal(url.searchParams.get('destination'), 'Cafe & Arte #1, Uberaba');
  assert.equal(url.searchParams.has('origin'), false);
});
