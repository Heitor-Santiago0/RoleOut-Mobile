import { useState } from 'react';
import { Image, Pressable, Text, View } from 'react-native';
import { useSocialResource } from './useSocialResource';
import { styles as s } from './theme';

/** Read with authentication, keep only in screen memory, revalidate on focus/resume. */
export function FeedCover({ token, id, title, open }: { token: string; id: string; title: string; open: () => void }) {
  const remote = useSocialResource<{ base64: string; legenda: string }>(`/comunidade/capa/${id}`, token);
  const [failed, setFailed] = useState(false);
  if (remote.data && !failed) return <Pressable accessibilityRole="button" accessibilityLabel={`Abrir fotos de ${title}`} onPress={open}>
    <Image source={{ uri: `data:image/jpeg;base64,${remote.data.base64}` }} accessibilityLabel={remote.data.legenda || `Foto de ${title}`} resizeMode="cover" style={{ width: '100%', aspectRatio: 4 / 3 }} onError={() => setFailed(true)} />
  </Pressable>;
  return <View style={{ aspectRatio: 4 / 3, justifyContent: 'center', alignItems: 'center', padding: 20 }}>
    {remote.loading ? <Text style={s.small}>Carregando foto…</Text> : <Pressable accessibilityRole="button" onPress={() => { setFailed(false); remote.reload(); }} style={{ padding: 16 }}><Text style={s.label}>Tentar carregar foto</Text></Pressable>}
  </View>;
}
