import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { Redirect, router } from 'expo-router';
import { Button, Message } from '../ui';
import { ProfileEditor } from '../ProfileEditor';
import { useAuth } from '../AuthContext';
import { Disclosure } from '../Disclosure';
import { PermissionSetup } from '../PermissionSetup';
import { SuggestionSettings } from '../SuggestionSettings';
import { TravelSettings } from '../TravelSettings';
import { PushSettings } from '../PushSettings';
import { styles as s } from '../theme';
export default function SettingsRoute() {
  const [revision,setRevision]=useState(0);
  const changed=()=>setRevision(value=>value+1);
  const auth=useAuth(); const {session}=auth;
  const [busy,setBusy]=useState(false);
  async function exit(){setBusy(true);try{await auth.exit();router.replace('/auth');}catch(e){auth.setMessage(e instanceof Error?e.message:'Não foi possível sair.');}finally{setBusy(false);}}
  if(!session)return <Redirect href="/auth" />;
  return <ScrollView contentContainerStyle={s.content}>
    <Text style={s.title}>Configurações</Text>
    <Button title="Meus interesses" secondary onPress={() => router.push('/preferences')} />
    <Disclosure title="Conta"><ProfileEditor session={session} /></Disclosure>
    <Disclosure title="Meus estabelecimentos"><Button title="Gerenciar locais" secondary onPress={() => router.push('/organizations')} /></Disclosure>
    {session.conta.administrador && <Disclosure title="Administração"><Button title="Analisar cadastros" secondary onPress={() => router.push('/reviews')} /><Button title="Analisar denúncias" secondary onPress={() => router.push('/moderation')} /></Disclosure>}
    <PermissionSetup token={session.token} revision={revision} />
    <Disclosure title="Sugestões automáticas"><SuggestionSettings token={session.token} onChanged={changed}/></Disclosure>
    <Disclosure title="Avisos em viagens"><TravelSettings token={session.token} onChanged={changed}/></Disclosure>
    <Disclosure title="Avisos de alterações"><PushSettings token={session.token} onChanged={changed}/></Disclosure>
    <Message text={auth.message} /><Button title="Sair da conta" secondary busy={busy} onPress={() => void exit()} />
  </ScrollView>;
}
