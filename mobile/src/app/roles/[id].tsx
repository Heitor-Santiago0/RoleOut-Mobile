import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { Text, View } from 'react-native';
import { useAuth } from '../../AuthContext';
import { DetailScreen } from '../../DetailScreen';
import { demoRoles } from '../../demo';
import { Button } from '../../ui';
import { styles as s } from '../../theme';
export default function DetailRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { session, demo } = useAuth();
  if (!session && !demo) return <Redirect href="/auth" />;
  const value = Number(id);
  const example = demoRoles.find(role => role.id === value);
  if (!Number.isSafeInteger(value) || value < 1 || (demo && !example)) return <View style={s.content}><Text style={s.text}>Rolê não encontrado.</Text><Button title="Voltar" onPress={() => router.replace('/explore')} /></View>;
  return <DetailScreen key={value} selected={demo ? example! : { id: value }} demo={demo} token={session?.token} onBack={() => { if (router.canGoBack()) router.back(); else router.replace('/explore'); }} />;
}
