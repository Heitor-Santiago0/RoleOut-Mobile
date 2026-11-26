import { Pressable, StyleSheet, Text, View } from 'react-native';
import { router, usePathname } from 'expo-router';
import { useAuth } from './AuthContext';
import { TabIcon } from './TabIcon';
import { colors } from './theme';
import { useRoutingPlan } from './RoutingPlanContext';

export function BackArrow({ onPress }: { onPress?: () => void }) {
  const { session, demo } = useAuth();
  const path = usePathname();
  if (!onPress && (['/', '/explore', '/feed', '/account'].includes(path))) return null;
  return <View style={styles.header}>
    <Pressable accessibilityRole="button" accessibilityLabel="Voltar" hitSlop={4} style={styles.back} onPress={onPress ?? (() => {
      if (router.canGoBack()) router.back();
      else router.replace(session || demo ? '/explore' : '/');
    })}><Text style={styles.arrow}>←</Text></Pressable>
  </View>;
}

export function BottomNavigation({ beforeNavigate }: { beforeNavigate?: () => void }) {
  const { session, demo } = useAuth();
  const { saved, startNew } = useRoutingPlan();
  const path = usePathname();
  if (!session && !demo) return null;
  const entries = [
    { href: '/explore', label: 'Explorar', kind: 'explore' },
    { href: '/feed', label: 'Feed', kind: 'feed' },
    { href: '/personalize', label: 'Criar rolê', kind: 'create' },
    { href: '/account', label: 'Perfil', kind: 'account' },
  ] as const;
  return <View style={styles.bar}>
    {entries.map(entry => {
      const selected = path === entry.href;
      return <Pressable key={entry.href} accessibilityRole="tab" accessibilityLabel={entry.label} accessibilityState={{ selected }} style={styles.tab} onPress={() => {
        beforeNavigate?.();
        if (entry.href === '/personalize' && saved) startNew();
        // Navigate also switches sibling tabs; dismissTo only handles stack history.
        router.navigate(entry.href);
      }}>
        <TabIcon kind={entry.kind} color={selected ? colors.blue : colors.muted} />
        <Text style={[styles.label, { color: selected ? colors.blue : colors.muted }]}>{entry.label}</Text>
      </Pressable>;
    })}
  </View>;
}

const styles = StyleSheet.create({
  header: { backgroundColor: colors.background, paddingHorizontal: 12, height: 48, justifyContent: 'center' },
  back: { width: 48, height: 44, alignItems: 'center', justifyContent: 'center' },
  arrow: { color: colors.text, fontSize: 28 },
  bar: { flexDirection: 'row', backgroundColor: colors.card, borderTopWidth: 1, borderColor: colors.border, paddingVertical: 6 },
  tab: { flex: 1, minHeight: 56, alignItems: 'center', justifyContent: 'center', gap: 2 },
  label: { fontSize: 12, fontWeight: '600' },
});
