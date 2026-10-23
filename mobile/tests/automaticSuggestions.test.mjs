import test from 'node:test';
import assert from 'node:assert/strict';
import { freshContext, suggestionFrequencies } from '../src/automaticSuggestions.ts';
test('automatic suggestions require recent valid location and a city', () => {
  const point = { latitude: -19.75, longitude: -47.93, cidade: 'Uberaba', timestamp: 1000000 };
  assert.equal(freshContext(point, 1000000), true);
  assert.equal(freshContext(point, 1119999), true);
  assert.equal(freshContext(point, 1120000), false);
  assert.equal(freshContext(point, 999999), false);
  for (const bad of [null, { ...point, cidade: ' ' }, { ...point, latitude: NaN }, { ...point, longitude: 181 }, { ...point, timestamp: NaN }]) assert.equal(freshContext(bad, 1000000), false);
});
test('frequency options include daily policy and quieter alternatives', () => {
  assert.deepEqual(suggestionFrequencies.map(v => v.hours), [0, 24, 72, 168]);
});
