import { NearbyMap } from './NearbyMap';
import { PreferenceHighlights } from './PreferenceHighlights';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { roles, type Role } from './api';
import { demoRoles } from './demo';
import { Button, DemoBanner, Message, price } from './ui';
import { colors, styles as s } from './theme';
export function ExploreScreen({ demo, token, onSelect }: { demo: boolean; token?: string; onSelect: (role: Role | { id: number }) => void }) {
  const [cidade, setCidade] = useState(''); const [filter, setFilter] = useState('');
  const [category, setCategory] = useState('Todos'); const [page, setPage] = useState(0);
  const [items, setItems] = useState<Role[]>([]); const [more, setMore] = useState(false);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    const result = demo ? Promise.resolve({ itens: demoRoles.filter(r => (!filter || r.cidade.toLowerCase() === filter.toLowerCase()) && (category === 'Todos' || r.categoria === category)) }) : roles(filter, category, page * 20, token);
    result.then(data => { if (active) { setItems(previous => page === 0 ? data.itens : [...previous, ...data.itens.filter(r => !previous.some(p => p.id === r.id))]); setMore(!demo && data.itens.length === 20); } })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : 'Não foi possível carregar.'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [demo, token, filter, category, page, retry]);
  return <ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    {demo && <DemoBanner />}<Text style={s.title}>Explorar</Text><Text style={s.subtitle}>Encontre seu próximo rolê.</Text><NearbyMap demo={demo} token={token} onSelect={id => onSelect({ id })} />
    <TextInput accessibilityLabel="Buscar por cidade" placeholder="Buscar por cidade" placeholderTextColor="#667085" style={s.field} value={cidade} onChangeText={setCidade} onSubmitEditing={() => { setBusy(true); setError(''); setItems([]); setPage(0); setFilter(cidade.trim()); setRetry(v => v + 1); }} />
    <Button title="Buscar" secondary onPress={() => { setBusy(true); setError(''); setItems([]); setPage(0); setFilter(cidade.trim()); setRetry(v => v + 1); }} />
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
      {['Todos', 'Cultura', 'Música', 'Gastronomia'].map(c => <Pressable key={c} accessibilityRole="button" accessibilityState={{ selected: c === category }} style={[s.chip, c === category && s.selected]} onPress={() => { if (c !== category) { setBusy(true); setError(''); setItems([]); setPage(0); setCategory(c); } }}><Text style={[s.chipText, c === category && s.selectedText]}>{c}</Text></Pressable>)}
    </ScrollView>
    {!demo && token && <PreferenceHighlights key={token} token={token} items={items} onSelect={onSelect} />}
    <Text style={[s.text, { fontWeight: '700' }]}>Rolês para explorar</Text>
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando rolês" />}<Message text={error} />
    {!!error && <Button title="Tentar novamente" onPress={() => { setBusy(true); setError(''); setRetry(v => v + 1); }} />}
    {!busy && !error && items.length === 0 && <Text style={s.subtitle}>Nenhum rolê encontrado. Experimente outra cidade ou categoria.</Text>}
    {Array.from({ length: Math.ceil(items.length / 2) }, (_, row) => <View key={row} style={s.row}>{items.slice(row * 2, row * 2 + 2).map(role => <Pressable key={role.id} style={s.card} accessibilityRole="button" accessibilityLabel={`Ver ${role.nome}`} onPress={() => onSelect(role)}><View style={s.tile}><Text style={s.tileText}>{role.categoria ?? 'Rolê'}</Text></View><View style={s.cardBody}><Text style={s.cardTitle}>{role.nome}</Text><Text style={s.small}>{role.cidade}</Text><Text style={s.text}>{price(role.valor)}</Text></View></Pressable>)}{items.slice(row * 2, row * 2 + 2).length === 1 && <View style={{ flex: 1 }} />}</View>)}
    {more && !error && <Button title="Ver mais" onPress={() => { setBusy(true); setError(''); setPage(v => v + 1); }} busy={busy} />}
  </ScrollView>;
}
