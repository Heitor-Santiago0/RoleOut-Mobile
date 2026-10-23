import type { MapStyleElement } from 'react-native-maps';

// Explicit road visibility and contrast for the joint user/outing overview.
// The SDK still decides which geometry and labels exist at each zoom level.
export const nearbyMapStyle: MapStyleElement[] = [
  { featureType: 'landscape.man_made', elementType: 'geometry', stylers: [{ color: '#eef1f5' }] },
  { featureType: 'road', elementType: 'geometry', stylers: [{ visibility: 'on' }] },
  { featureType: 'road', elementType: 'geometry.fill', stylers: [{ color: '#ffffff' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#8995a7' }] },
  { featureType: 'road.local', elementType: 'geometry', stylers: [{ visibility: 'on' }] },
  { featureType: 'road.arterial', elementType: 'geometry.fill', stylers: [{ color: '#fff4d6' }] },
  { featureType: 'road.highway', elementType: 'geometry.fill', stylers: [{ color: '#f5d38a' }] },
  { featureType: 'road', elementType: 'labels', stylers: [{ visibility: 'on' }] },
  { featureType: 'road', elementType: 'labels.text.fill', stylers: [{ color: '#334155' }] },
  { featureType: 'road', elementType: 'labels.text.stroke', stylers: [{ color: '#ffffff' }] },
];
