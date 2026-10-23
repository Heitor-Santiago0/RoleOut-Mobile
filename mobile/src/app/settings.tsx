import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { Redirect } from 'expo-router';
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
  const {session}=useAuth();
  if(!session)return <Redirect href="/auth" />;
  return <ScrollView contentContainerStyle={s.content}>
    <Text style={s.title}>Configurações</Text>
    <PermissionSetup token={session.token} revision={revision} />
    <Disclosure title="Sugestões automáticas"><SuggestionSettings token={session.token} onChanged={changed}/></Disclosure>
    <Disclosure title="Avisos em viagens"><TravelSettings token={session.token} onChanged={changed}/></Disclosure>
    <Disclosure title="Avisos de alterações"><PushSettings token={session.token} onChanged={changed}/></Disclosure>
  </ScrollView>;
}
