import { useEffect, useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { router } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { ProfileEditor } from '../../ProfileEditor';
import { ApiError, request } from '../../api';
import { Button, DemoBanner, Message } from '../../ui';
import { styles as s } from '../../theme';
export default function AccountRoute() {
  const auth = useAuth(); const [busy, setBusy] = useState(false);
  const { session, expire, setMessage } = auth;
  useEffect(() => {
    if (!session) return;
    let active = true;
    request('/auth/me', { token: session.token }).catch(async e => {
      if (!active) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setMessage(e instanceof Error ? e.message : 'Não foi possível verificar a conta.');
    });
    return () => { active = false; };
  }, [session, expire, setMessage]);
  async function exit() {
    setBusy(true); auth.setMessage('');
    try { await auth.exit(); router.replace('/auth'); }
    catch (e) { auth.setMessage(e instanceof Error ? e.message : 'Não foi possível sair.'); }
    finally { setBusy(false); }
  }
  return <ScrollView contentContainerStyle={s.content}>{auth.demo && <DemoBanner />}<Text style={s.title}>Minha conta</Text><Text style={s.text}>{auth.demo ? 'Visitante da demonstração' : auth.session?.conta.nome}</Text>{auth.session && <Text style={s.subtitle}>{auth.session.conta.email}</Text>}<Message text={auth.message} />{auth.session && <><ProfileEditor key={auth.session.token} session={auth.session} /><Button title="Comunidade" secondary onPress={() => router.push('/community')} /><Button title="Meu perfil público" secondary onPress={() => router.push('/social-profile')} /><Button title="Configurações" secondary onPress={() => router.push('/settings')} />{auth.session.conta.administrador && <Button title="Analisar cadastros" secondary onPress={() => router.push('/reviews')} />}{auth.session.conta.administrador && <Button title="Analisar denúncias" secondary onPress={() => router.push('/moderation')} />}<Button title="Meus locais" secondary onPress={() => router.push('/organizations')} /><Button title="Minhas preferências" secondary onPress={() => router.push('/preferences')} /><Button title="Meus rolês" secondary onPress={() => router.push('/itineraries')} /></>}<Button title={auth.demo ? 'Voltar para a entrada' : 'Sair da conta'} onPress={exit} busy={busy} /></ScrollView>;
}
