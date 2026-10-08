import { Redirect, Tabs } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { colors } from '../../theme';
export default function TabLayout() {
  const { session, demo } = useAuth();
  if (!session && !demo) return <Redirect href="/auth" />;
  return <Tabs screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: colors.background }, tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.border }, tabBarActiveTintColor: colors.blue, tabBarInactiveTintColor: colors.muted, tabBarIcon: () => null }}><Tabs.Screen name="personalize" options={{ title: 'Criar rolê' }} /><Tabs.Screen name="explore" options={{ title: 'Explorar' }} /><Tabs.Screen name="account" options={{ title: 'Conta' }} /></Tabs>;
}
