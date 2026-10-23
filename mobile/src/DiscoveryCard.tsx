import { Pressable, Text, View } from 'react-native';
import { RoleIllustration } from './RoleIllustration';
import { styles as s } from './theme';

export function DiscoveryCard({ name, category, subtitle, footer, onPress }: { name: string; category?: string | null; subtitle: string; footer?: string; onPress: () => void }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={`Ver ${name}. ${subtitle}`} onPress={onPress} style={({ pressed }) => [s.card, { flex: 0, borderRadius: 20, opacity: pressed ? 0.8 : 1 }]}>
    <RoleIllustration name={name} category={category} />
    <View style={[s.cardBody, { padding: 18, gap: 8 }]}>
      {!!category && <Text style={{ color: '#9DBFFF', fontSize: 12, fontWeight: '700', letterSpacing: 1 }}>{category.toLocaleUpperCase('pt-BR')}</Text>}
      <Text style={[s.cardTitle, { fontSize: 19, lineHeight: 26 }]}>{name}</Text>
      <Text style={s.small}>{subtitle}</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 8 }}>
        {!!footer && <Text style={[s.text, { fontWeight: '600' }]}>{footer}</Text>}
        <Text style={{ color: '#9DBFFF', fontSize: 14, fontWeight: '600' }}>Conhecer lugar →</Text>
      </View>
    </View>
  </Pressable>;
}
