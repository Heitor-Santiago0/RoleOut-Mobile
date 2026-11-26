import { ScrollView, Pressable, Text, View } from 'react-native';
import { router } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { Button, DemoBanner, Message } from '../../ui';
import { styles as s } from '../../theme';
import { useSocialResource } from '../../useSocialResource';
import type { OwnProfile } from '../../socialProfile';
import { ProfileHero, type ProfileDetails } from '../../ProfileHero';
import { PublicationsList } from '../../Publications';
import { Disclosure } from '../../Disclosure';
import { FollowedVenues } from '../../VenueFollow';
export default function AccountRoute() {
  const { session } = useAuth();
  if (!session) return <ScrollView contentContainerStyle={s.content}><DemoBanner /><Text style={s.title}>Seu perfil</Text><Button title="Entrar na minha conta" onPress={() => router.push('/auth')} /></ScrollView>;
  return <Account token={session.token} name={session.conta.nome} />;
}
function Account({ token, name }: { token: string; name: string }) {
  const profile = useSocialResource<OwnProfile>('/perfil-social', token);
  const details = useSocialResource<ProfileDetails>('/perfil-detalhes', token);
  return <ScrollView contentContainerStyle={s.content}>
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}><Text style={s.title}>Perfil</Text><Pressable accessibilityRole="button" accessibilityLabel="Configurações" style={{ padding: 12 }} onPress={() => router.push('/settings')}><Text style={{ color: '#BAC1D5', fontSize: 26 }}>⚙</Text></Pressable></View>
    <Message text={profile.error || details.error} />
    <ProfileHero name={profile.data?.nome || name} bio={profile.data?.apresentacao || ''} details={details.data} />
    <View style={s.row}><View style={{ flex: 1 }}><Button title="Editar perfil" secondary onPress={() => router.push('/social-profile')} /></View><View style={{ flex: 1 }}><Button title="Meus rolês" onPress={() => router.push('/itineraries')} /></View></View>
    <Disclosure title="Locais que sigo"><FollowedVenues token={token} /></Disclosure>
    {profile.data?.publico && profile.data.id ? <PublicationsList token={token} author={profile.data.id} /> : <Text style={s.small}>Perfil privado. Você controla a visibilidade em Editar perfil.</Text>}
    {!!(profile.error || details.error) && <Button title="Tentar novamente" secondary onPress={() => { profile.reload(); details.reload(); }} />}
  </ScrollView>;
}
