import test from 'node:test';
import assert from 'node:assert/strict';
import { coordinates } from '../src/coordinates.ts';

test('optional coordinates preserve zero and accept decimal comma', () => {
  assert.deepEqual(coordinates('', ' '), { latitude: null, longitude: null });
  assert.deepEqual(coordinates('0', '0'), { latitude: 0, longitude: 0 });
  assert.deepEqual(coordinates(' -19,750001 ', '-47.930001'), { latitude: -19.750001, longitude: -47.930001 });
  assert.deepEqual(coordinates('-90', '180'), { latitude: -90, longitude: 180 });
});
test('invalid or partial coordinates cannot silently become zero or be rounded', () => {
  for (const pair of [['1',''], ['','1'], ['90.1','0'], ['0','-180.1'], ['NaN','0'], ['1.1234567','2'], ['1e1','2'], ['1,2,3','2']]) assert.equal(coordinates(...pair), null);
});
