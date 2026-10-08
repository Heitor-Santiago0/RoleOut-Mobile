import { ScrollView, View } from 'react-native';
import { Redirect, router } from 'expo-router';
import { useAuth } from '../AuthContext';
import { Button, Message } from '../ui';
import { styles as s } from '../theme';
import { Brand } from '../Brand';
export default function Welcome() {
  const { session, demo, message } = useAuth();
  if (session || demo) return <Redirect href="/explore" />;
  return <ScrollView contentContainerStyle={[s.content, { justifyContent: 'center', alignItems: 'center', gap: 72 }]}><Brand /><View style={{ width: '100%', maxWidth: 340 }}><Message text={message} /><Button title="Começar" onPress={() => router.push('/auth')} /></View></ScrollView>;
}
