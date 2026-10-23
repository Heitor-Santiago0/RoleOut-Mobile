import { useState, type ReactNode } from 'react';
import { Pressable, Text, View } from 'react-native';
import { styles as s } from './theme';

export function Disclosure({ title, children }: { title: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return <View style={{ gap: open ? 8 : 0 }}>
    <Pressable accessibilityRole="button" accessibilityState={{ expanded: open }} onPress={() => setOpen(v => !v)} style={{ minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
      <Text style={[s.small, { color: '#9DBFFF', flex: 1 }]}>{title}</Text>
      <Text style={s.small}>{open ? '−' : '+'}</Text>
    </Pressable>
    {open && children}
  </View>;
}
