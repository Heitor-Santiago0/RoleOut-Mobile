import { Redirect, Tabs } from 'expo-router';
import { useAuth } from '../../AuthContext';
import { colors } from '../../theme';
import { TabIcon } from '../../TabIcon';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
export default function TabLayout() {
  const { session, demo } = useAuth();
  const insets = useSafeAreaInsets();
  if (!session && !demo) return <Redirect href="/auth" />;
  return <Tabs screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: colors.background }, tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.border, height: 64 + insets.bottom, paddingTop: 8, paddingBottom: Math.max(insets.bottom, 8) }, tabBarShowLabel: false, tabBarActiveTintColor: colors.blue, tabBarInactiveTintColor: colors.muted }}>
    <Tabs.Screen name="personalize" options={{ title: 'Criar rolê', tabBarAccessibilityLabel: 'Criar rolê', tabBarIcon: ({ color }) => <TabIcon kind="create" color={color} /> }} />
    <Tabs.Screen name="explore" options={{ title: 'Explorar', tabBarAccessibilityLabel: 'Explorar', tabBarIcon: ({ color }) => <TabIcon kind="explore" color={color} /> }} />
    <Tabs.Screen name="account" options={{ title: 'Conta', tabBarAccessibilityLabel: 'Minha conta', tabBarIcon: ({ color }) => <TabIcon kind="account" color={color} /> }} />
  </Tabs>;
}
