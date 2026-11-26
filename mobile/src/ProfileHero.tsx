import { useState } from 'react';
import { Image, Pressable, Text, View } from 'react-native';
import { styles as s } from './theme';
export type ProfileDetails = { fotoBase64: string | null; interessesPublicos: boolean; interesses: string[]; publicacoes: number; seguidores: number; seguindo: number };
export function ProfileHero({ name, bio, details }: { name: string; bio: string; details: ProfileDetails | null }) {
  const [expanded, setExpanded] = useState(false);
  return <View style={{ gap: 16 }}>
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
      {details?.fotoBase64 ? <Image accessibilityLabel={`Foto de ${name}`} source={{ uri: `data:image/jpeg;base64,${details.fotoBase64}` }} style={{ width: 76, height: 76, borderRadius: 38 }} /> : <View style={{ width: 76, height: 76, borderRadius: 38, backgroundColor: '#303E65', alignItems: 'center', justifyContent: 'center' }}><Text style={s.title}>{Array.from(name)[0]?.toLocaleUpperCase()}</Text></View>}
      <View style={{ flex: 1, flexDirection: 'row', justifyContent: 'space-around', gap: 4 }}>{[['Rolês', details?.publicacoes], ['Seguidores', details?.seguidores], ['Seguindo', details?.seguindo]].map(([label, count]) => <View key={label} style={{ alignItems: 'center', gap: 3 }}><Text style={s.cardTitle}>{count ?? '—'}</Text><Text style={[s.small, { fontSize: 11 }]}>{label}</Text></View>)}</View>
    </View>
    <View style={{ gap: 6 }}><Text style={s.cardTitle}>{name}</Text>{!!bio && <Text style={s.text}>{bio}</Text>}</View>
    {!!details?.interesses.length && <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>{details.interesses.slice(0, expanded ? undefined : 5).map(interest => <View key={interest} style={[s.chip, { paddingVertical: 5, paddingHorizontal: 10 }]}><Text style={[s.chipText, { fontSize: 12 }]}>{interest}</Text></View>)}</View>}
    {!!details && details.interesses.length > 5 && <Pressable accessibilityRole="button" onPress={() => setExpanded(!expanded)} style={{ paddingVertical: 8 }}><Text style={s.small}>{expanded ? 'Mostrar menos' : `+${details.interesses.length - 5} interesses`}</Text></Pressable>}
  </View>;
}
