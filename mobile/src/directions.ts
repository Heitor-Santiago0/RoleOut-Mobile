import type { Role } from './api';

type Destination = Pick<Role, 'local' | 'cidade' | 'latitude' | 'longitude' | 'cancelado'>;
export function hasCoordinates(role: Destination): boolean {
  return typeof role.latitude === 'number' && Number.isFinite(role.latitude) && Math.abs(role.latitude) <= 90
    && typeof role.longitude === 'number' && Number.isFinite(role.longitude) && Math.abs(role.longitude) <= 180;
}
export function directionsUrl(role: Destination): string | null {
  if (role.cancelado) return null;
  if (hasCoordinates(role)) return 'https://www.google.com/maps/dir/?' + new URLSearchParams({ api: '1', destination: `${role.latitude},${role.longitude}` });
  if (!role.local?.trim() || !role.cidade.trim()) return null;
  return 'https://www.google.com/maps/dir/?' + new URLSearchParams({
    api: '1', destination: `${role.local.trim()}, ${role.cidade.trim()}`,
  });
}
