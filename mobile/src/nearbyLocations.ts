import type { Point } from './NearbyMap.types';

export type NearbyLocation = { key: string; latitude: number; longitude: number; points: Point[] };

// Group only identical coordinates: nearby addresses must remain distinct.
export function nearbyLocations(points: readonly Point[]): NearbyLocation[] {
  const locations = new Map<string, NearbyLocation>();
  for (const point of points) {
    const key = `${point.latitude},${point.longitude}`;
    let location = locations.get(key);
    if (!location) {
      location = { key, latitude: point.latitude, longitude: point.longitude, points: [] };
      locations.set(key, location);
    }
    location.points.push(point);
  }
  return [...locations.values()];
}
