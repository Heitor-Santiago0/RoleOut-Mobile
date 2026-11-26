import { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { router } from 'expo-router';
import { useSocialResource } from './useSocialResource';
import { Button, Message } from './ui';
import { styles as s } from './theme';
import { PublicationContent } from './PublicationContent';
import type { PublicPublication, PublicationPage } from './publication';
import { ItineraryCredits } from './Community';

export function PublicationsList({ token, author }: { token: string; author: string }) {
  const [pages, setPages] = useState<string[]>(['']);
  const after = pages[pages.length - 1];
  const remote = useSocialResource<PublicationPage>(`/publicacoes?autor=${encodeURIComponent(author)}${after ? `&apos=${encodeURIComponent(after)}` : ''}`, token);
  return <View style={{ gap: 14 }}><Text style={s.cardTitle}>Passeios compartilhados</Text>
    {remote.loading && <Text style={s.small}>Carregando publicações…</Text>}<Message text={remote.error} />
    {remote.data?.itens.map(item => <View key={item.id} style={[s.secondary, { padding: 18, borderRadius: 16, gap: 10 }]}>
      <Text style={s.cardTitle}>{item.titulo}</Text><Text style={s.small}>{item.cidade} · {item.totalParadas} paradas · {item.totalFotos} fotos</Text>
      <Button title="Conhecer passeio" secondary onPress={() => router.push({ pathname: '/publications/[id]', params: { id: item.id } })} />
    </View>)}
    {remote.data?.itens.length === 0 && <Text style={s.small}>Nenhum passeio compartilhado nesta página.</Text>}
    {remote.data?.proximo && <Button title="Mais passeios" secondary onPress={() => setPages([...pages, remote.data!.proximo!])} />}
    {pages.length > 1 && <Button title="Página anterior" secondary disabled={remote.loading} onPress={() => setPages(pages.slice(0, -1))} />}
    {!!remote.error && <Button title="Tentar novamente" secondary onPress={remote.reload} />}
  </View>;
}
export function PublicPublicationScreen({ token, id }: { token: string; id: string }) {
  const remote = useSocialResource<PublicPublication>(`/publicacoes/${encodeURIComponent(id)}`, token);
  return <ScrollView contentContainerStyle={s.content}>
    {remote.loading && <Text style={s.small}>Carregando passeio…</Text>}<Message text={remote.error} />
    {remote.data && <><Text style={s.label}>Por {remote.data.autor.nome}</Text><ItineraryCredits token={token} id={id} publication /><PublicationContent value={remote.data} />
      <Button title="Copiar rolê" onPress={() => router.push({ pathname: '/copy/[id]', params: { id } })} />
      <Button title="Comentários" secondary onPress={() => router.push({ pathname: '/community-texts', params: { tipo: 'COMENTARIO', alvo: id } })} />
      <Button title="Denunciar publicação" secondary onPress={() => router.push({ pathname: '/report', params: { tipo: 'PUBLICACAO', alvo: id } })} />
      <Button title="Ver perfil do autor" secondary onPress={() => router.push({ pathname: '/people/[id]', params: { id: remote.data!.autor.id } })} /></>}
    <Button title="Atualizar publicação" secondary disabled={remote.loading} onPress={remote.reload} />
  </ScrollView>;
}
