import { Image, Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { styles as s } from './theme';

// Exact fixture names only: illustrative pictures must not impersonate real venues.
const pictures: Record<string, { source: number; label: string }> = {
  'Teste - Encontro no cafe': { source: require('../assets/places/cafe.png'), label: 'Café acolhedor com plantas e mesa de madeira' },
  'Teste - Feira de sabores': { source: require('../assets/places/feira.png'), label: 'Feira gastronômica ao entardecer' },
  'Teste - Musica ao vivo': { source: require('../assets/places/musica.png'), label: 'Palco intimista com violão e microfone' },
  'Teste - Exposicao de arte': { source: require('../assets/places/arte.png'), label: 'Galeria com obras de arte coloridas' },
  'Teste - Oficina de pintura': { source: require('../assets/places/pintura.png'), label: 'Ateliê de pintura com tela e pincéis' },
  'Teste - Passeio ao ar livre': { source: require('../assets/places/parque.png'), label: 'Caminho arborizado em um parque' },
};

const realPictures: Record<string, { source: number; author: string; year: string; page: string }> = {
  'Mercado Municipal de Uberaba': { source: require('../assets/places/uberaba/mercado.jpg'), author: 'Pedroepaulo', year: '2018', page: 'https://commons.wikimedia.org/wiki/File:Mercado-municipal-uberaba.jpg' },
  'Parque das Acácias (Piscinão)': { source: require('../assets/places/uberaba/acacias.jpg'), author: 'Willemarcel', year: '2017', page: 'https://commons.wikimedia.org/wiki/File:Parque_das_Ac%C3%A1cias,_Uberaba.jpg' },
  'Parque Municipal Mata do Ipê': { source: require('../assets/places/uberaba/mata-ipe.jpg'), author: 'Willemarcel', year: '2017', page: 'https://commons.wikimedia.org/wiki/File:Parque_Mata_do_Ip%C3%AA,_Uberaba.jpg' },
};
export function RoleIllustration({ name, category, height = 170 }: { name: string; category?: string | null; height?: number }) {
  const picture = pictures[name], real = realPictures[name];
  if (real) return <View style={[art.frame, { height }]}><Image source={real.source} resizeMode="cover" style={art.image} accessibilityLabel={`${name}. Fotografia de ${real.year}, por ${real.author}.`} /><Pressable style={art.badge} accessibilityRole="link" accessibilityLabel="Créditos e licença da fotografia" onPress={event => { event.stopPropagation(); void Linking.openURL(real.page).catch(() => undefined); }}><Text style={art.caption}>{real.author} · {real.year} · CC BY-SA 4.0 ↗</Text></Pressable></View>;
  return <View style={[art.frame, { height }]}>
    {picture ? <Image source={picture.source} style={art.image} resizeMode="cover" accessibilityLabel={`Imagem ilustrativa: ${picture.label}. Local fictício.`} /> : <Text style={s.tileText}>{category || 'Descubra um novo rolê'}</Text>}
    {picture && <View style={art.badge}><Text style={art.caption}>Imagem ilustrativa</Text></View>}
  </View>;
}
const art = StyleSheet.create({
  image: { position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' },
  frame: { backgroundColor: '#29324B', overflow: 'hidden', justifyContent: 'center', alignItems: 'center', width: '100%' },
  badge: { position: 'absolute', bottom: 10, left: 10, backgroundColor: '#161824E8', borderRadius: 8, paddingHorizontal: 9, paddingVertical: 5 },
  caption: { color: '#FFFFFF', fontSize: 11, fontWeight: '600' },
});
