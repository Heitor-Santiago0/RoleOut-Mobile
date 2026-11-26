import { categories as roleCategories } from './categories';
import { AutomaticSuggestionCard } from './AutomaticSuggestionCard';
import type { DiscoveryContext } from './automaticSuggestions';
import type { Point } from './NearbyMap.types';
import { DiscoveryCard } from './DiscoveryCard';
import { NearbyMap } from './NearbyMap';
import { PreferenceHighlights } from './PreferenceHighlights';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { roles, type Role } from './api';
import { demoRoles } from './demo';
import { Button, DemoBanner, Message, price } from './ui';
import { colors, styles as s } from './theme';
import { router } from 'expo-router';
import { useRoutingPlan } from './RoutingPlanContext';
import { UpcomingItineraryCard } from './UpcomingItineraryCard';
export function ExploreScreen({ demo, token, onSelect }: { demo: boolean; token?: string; onSelect: (role: Role | { id: number }) => void }) {
  const { stops } = useRoutingPlan();
  const [editingCity, setEditingCity] = useState(false);
  const [cidade, setCidade] = useState(''); const [filter, setFilter] = useState(demo ? 'Uberaba' : '');
  const [category, setCategory] = useState('Todos'); const [page, setPage] = useState(0);
  const [items, setItems] = useState<Role[]>([]); const [more, setMore] = useState(false);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [retry, setRetry] = useState(0);
  const [discoveryContext, setDiscoveryContext] = useState<DiscoveryContext | null>(null);
  const [nearby, setNearby] = useState<Point[] | null>(null);
  const manualCity = useRef(false);
  const detectedCity = useRef('');
  const onCity = useCallback((city: string) => {
    if (manualCity.current || detectedCity.current === city) return;
    detectedCity.current = city;
    setCidade(city); setFilter(city); setItems([]); setPage(0); setBusy(true); setError('');
  }, []);
  function searchCity() {
    if (!cidade.trim()) { setError('Informe a cidade que deseja explorar.'); return; }
    setEditingCity(false);
    manualCity.current = true;
    setBusy(true); setError(''); setItems([]); setPage(0); setFilter(cidade.trim()); setRetry(v => v + 1);
  }
  useEffect(() => {
    if (!filter) return;

    let active = true;
    const result = demo ? Promise.resolve({ itens: demoRoles.filter(r => (!filter || r.cidade.toLowerCase() === filter.toLowerCase()) && (category === 'Todos' || r.categoria === category)) }) : roles(filter, category, page * 20, token);
    result.then(data => { if (active) { setItems(previous => page === 0 ? data.itens : [...previous, ...data.itens.filter(r => !previous.some(p => p.id === r.id))]); setMore(!demo && data.itens.length === 20); } })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : 'Não foi possível carregar.'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [demo, token, filter, category, page, retry]);
  return <ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    {demo && <DemoBanner />}<Text style={s.title}>Explorar</Text>
    {stops.length > 0 && <Button title={`Organizar rolê · ${stops.length} parada(s)`} onPress={() => router.push('/organize')} />}
    {!demo && token && <UpcomingItineraryCard key={`upcoming:${token}`} token={token} />}
    <NearbyMap demo={demo} token={token} onSelect={id => onSelect({ id })} onCity={onCity} onNearby={setNearby} onContext={setDiscoveryContext} />
    {!demo && token && <AutomaticSuggestionCard key={`automatic:${token}`} token={token} context={discoveryContext} onSelect={onSelect} />}
    {!demo && token && <PreferenceHighlights key={`recommendations:${token}`} token={token} items={nearby} onSelect={onSelect} />}
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
      <Text accessibilityRole="header" style={[s.cardTitle, { flex: 1 }]}>{filter ? `Rolês em ${filter}` : 'Rolês na cidade'}</Text>
      {!!filter && <Pressable accessibilityRole="button" accessibilityState={{ expanded: editingCity }} onPress={() => setEditingCity(v => !v)} style={{ minHeight: 44, justifyContent: 'center' }}><Text style={{ color: '#9DBFFF', fontWeight: '600' }}>{editingCity ? 'Fechar' : 'Trocar cidade'}</Text></Pressable>}
    </View>
    {(editingCity || !filter) && <>
    <TextInput accessibilityLabel="Buscar por cidade" placeholder="Buscar por cidade" placeholderTextColor="#667085" style={s.field} value={cidade} onChangeText={setCidade} returnKeyType="search" onSubmitEditing={searchCity} />
    <Button title="Ver rolês nesta cidade" secondary onPress={searchCity} /></>}
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingRight: 12 }} accessibilityLabel="Categorias de rolês">
      {['Todos', ...roleCategories].map(c => <Pressable key={c} accessibilityRole="button" accessibilityState={{ selected: c === category }} style={[s.chip, c === category && s.selected]} onPress={() => { if (c !== category) { setCategory(c); setPage(0); setMore(false); setItems([]); if (filter) { setBusy(true); setError(''); } else { setError('Selecione a cidade ou ative a localização para ver esta categoria.'); } } }}><Text style={[s.chipText, c === category && s.selectedText]}>{c}</Text></Pressable>)}
    </ScrollView>
    {!filter && <Text style={s.subtitle}>Ative a localização ou informe sua cidade para ver os rolês.</Text>}
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando rolês" />}<Message text={error} />
    {!!error && !!filter && <Button title="Tentar novamente" onPress={() => { setBusy(true); setError(''); setRetry(v => v + 1); }} />}
    {!busy && !error && !!filter && items.length === 0 && <Text style={s.subtitle}>Nenhum rolê encontrado. Experimente outra cidade ou categoria.</Text>}
    {items.map(role => <DiscoveryCard key={role.id} name={role.nome} category={role.categoria} subtitle={role.local ? `${role.local} · ${role.cidade}` : role.cidade} footer={price(role.valor)} onPress={() => onSelect(role)} />)}
    {more && !error && <Button title="Ver mais" onPress={() => { setBusy(true); setError(''); setPage(v => v + 1); }} busy={busy} />}
  </ScrollView>;
}
