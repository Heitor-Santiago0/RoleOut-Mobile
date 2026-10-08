import { Text, View } from 'react-native';
import { styles as s } from './theme';
import type { NearbyMapProps } from './NearbyMap.types';
export function NearbyMap({ demo }: NearbyMapProps) {
  return <View style={[s.secondary, { padding: 18, borderRadius: 14, gap: 8 }]}><Text style={s.label}>Rolês perto de você</Text><Text style={s.subtitle}>{demo ? 'Os exemplos fictícios não possuem localização real.' : 'Abra o app no Android ou iPhone para ativar o mapa e acompanhar sua localização. A prévia no navegador mostra a listagem.'}</Text></View>;
}
