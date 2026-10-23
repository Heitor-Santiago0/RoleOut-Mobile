import test from 'node:test';
import assert from 'node:assert/strict';
import { nearbyLocations } from '../src/nearbyLocations.ts';

test('coincident outings remain individually selectable without moving their coordinates', () => {
  const points = [
    { id: 9, latitude: -19.750001, longitude: -47.930001 },
    { id: 11, latitude: -19.750001, longitude: -47.930001 },
    { id: 13, latitude: -19.75, longitude: -47.930001 },
    { id: 15, latitude: -19.750001, longitude: -47.930001 },
  ];
  const original = JSON.stringify(points);
  const groups = nearbyLocations(points);
  assert.equal(groups.length, 2);
  assert.deepEqual(groups[0].points.map(p => p.id), [9, 11, 15]);
  assert.equal(groups[0].latitude, points[0].latitude);
  assert.equal(groups[0].longitude, points[0].longitude);
  assert.deepEqual(groups[1].points.map(p => p.id), [13]);
  assert.equal(JSON.stringify(points), original);
});

test('empty results and locations on the equator or prime meridian are supported', () => {
  assert.deepEqual(nearbyLocations([]), []);
  const groups = nearbyLocations([{ id: 1, latitude: 0, longitude: 1 }, { id: 2, latitude: 1, longitude: 0 }]);
  assert.equal(groups.length, 2);
  assert.notEqual(groups[0].key, groups[1].key);
});
