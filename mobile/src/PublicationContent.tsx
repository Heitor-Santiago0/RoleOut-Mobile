import { Image, Text, View } from 'react-native';
import { styles as s } from './theme';
import type { OwnPublication } from './publication';

export function PublicationContent({ value }: { value: Pick<OwnPublication, 'titulo' | 'cidade' | 'relato' | 'paradas' | 'fotos'> }) {
  return <View style={{ gap: 16 }}>
    <Text style={s.title}>{value.titulo}</Text><Text style={s.subtitle}>{value.cidade}</Text>
    {value.fotos.map((photo, index) => <View key={index} style={{ gap: 6 }}>
      <Image accessibilityLabel={photo.legenda || `Foto ${index + 1} do passeio`} source={{ uri: `data:image/jpeg;base64,${photo.base64}` }} style={{ width: '100%', height: 240, borderRadius: 18 }} resizeMode="contain" />
      {!!photo.legenda && <Text style={s.small}>{photo.legenda}</Text>}
    </View>)}
    {!!value.relato && <Text style={s.text}>{value.relato}</Text>}
    <Text style={s.cardTitle}>Paradas do passeio</Text>
    {value.paradas.map((stop, index) => <View key={`${index}:${stop.roleId}`} style={[s.secondary, { padding: 16, borderRadius: 14, gap: 6 }]}>
      <Text style={s.label}>{index + 1}. {stop.nome}</Text><Text style={s.small}>{stop.local || 'Local não informado'} · {stop.minutos} min de visita planejada</Text>
    </View>)}
    <Text style={s.small}>Experiência compartilhada pelo autor. Horários, preços e disponibilidade podem mudar.</Text>
  </View>;
}
