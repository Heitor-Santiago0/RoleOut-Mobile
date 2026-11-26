import { Redirect, Tabs } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { colors } from '../../theme';
export default function TabLayout() {
  const { session, demo } = useAuth();
  if (!session && !demo) return <Redirect href="/auth" />;
  return <Tabs backBehavior="history" tabBar={() => null} screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: colors.background } }}>
    <Tabs.Screen name="personalize" options={{ title: 'Criar rolê' }} />
    <Tabs.Screen name="explore" options={{ title: 'Explorar' }} />
    <Tabs.Screen name="feed" options={{ title: 'Feed' }} />
    <Tabs.Screen name="account" options={{ title: 'Perfil' }} />
  </Tabs>;
}
