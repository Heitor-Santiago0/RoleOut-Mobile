import { ProfileHero, type ProfileDetails } from './ProfileHero';
import { FollowButton } from './Community';
import { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { router } from 'expo-router';
import { Button, Message } from './ui';
import { styles as s } from './theme';
import { useSocialResource } from './useSocialResource';
import type { ProfilePage, PublicProfile } from './socialProfile';
import { PublicationsList } from './Publications';

export function PublicProfileScreen({ token, id }: { token: string; id: string }) {
  const remote = useSocialResource<PublicProfile>(`/perfis/${encodeURIComponent(id)}`, token);
  const details = useSocialResource<ProfileDetails>(`/perfil-detalhes/${encodeURIComponent(id)}`, token);
  return <ScrollView contentContainerStyle={s.content}>

    {remote.loading && <Text style={s.small}>Carregando perfil…</Text>}
    <Message text={remote.error || details.error} />
    {remote.data && <><ProfileHero name={remote.data.nome} bio={remote.data.apresentacao} details={details.data} /><FollowButton token={token} id={id} /></>}
    {remote.data && <PublicationsList key={remote.data.id} token={token} author={remote.data.id} />}
    {!!remote.error && <Button title="Tentar novamente" secondary onPress={remote.reload} />}
  </ScrollView>;
}
export function PublicProfiles({ token }: { token: string }) {
  const [pages, setPages] = useState<string[]>(['']);
  const after = pages[pages.length - 1];
  const remote = useSocialResource<ProfilePage>(`/perfis${after ? `?apos=${encodeURIComponent(after)}` : ''}`, token);
  return <ScrollView contentContainerStyle={s.content}>
    <Text style={s.title}>Pessoas no RolêOut</Text>
    <Text style={s.subtitle}>Conheça quem escolheu compartilhar seu perfil.</Text>
    {remote.loading && <Text style={s.small}>Carregando pessoas…</Text>}
    <Message text={remote.error} />
    {remote.data?.itens.map(profile => <View key={profile.id} style={[s.secondary, { padding: 18, borderRadius: 16, gap: 10 }]}>
      <Text style={s.cardTitle}>{profile.nome}</Text>
      {!!profile.apresentacao && <Text style={s.small} numberOfLines={2}>{profile.apresentacao}</Text>}
      <Button title="Ver perfil" secondary onPress={() => router.push({ pathname: '/people/[id]', params: { id: profile.id } })} />
    </View>)}
    {remote.data?.itens.length === 0 && <Text style={s.text}>Ainda não há perfis públicos nesta página.</Text>}
    {remote.data?.proximo && <Button title="Mais pessoas" onPress={() => setPages([...pages, remote.data!.proximo!])} />}
    {pages.length > 1 && <Button title="Página anterior" secondary disabled={remote.loading} onPress={() => setPages(pages.slice(0, -1))} />}
    <Button title="Atualizar pessoas" secondary disabled={remote.loading} onPress={remote.reload} />
  </ScrollView>;
}
