import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { Redirect, router, useFocusEffect } from 'expo-router';
import { ApiError, request, type ItinerarySummary, type SavedItinerary } from './api';
import { useAuth } from './AuthContext';
import { Button, Message, price } from './ui';
import { colors, styles as s } from './theme';
import { schedule } from './itinerary';

export function SavedItinerariesScreen({ id }: { id?: number }) {
  const { session, demo, expire } = useAuth(); const token = session?.token;
  const [storedItems, setItems] = useState<ItinerarySummary[]>([]); const [storedDetail, setDetail] = useState<SavedItinerary | null>(null);
  const [loadedToken, setLoadedToken] = useState<string>();
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [more, setMore] = useState(false);
  const generation = useRef(0);
  const load = useCallback(async (offset = 0) => {
    if (!token) return;
    const version = ++generation.current; setBusy(true); setError('');
    try {
      if (id !== undefined) {
        if (!Number.isSafeInteger(id) || id < 1) throw new Error('Roteiro inválido.');
        const data = await request<SavedItinerary>(`/roteiros/${id}`, { token });
        if (version === generation.current) { setDetail(data); setLoadedToken(token); }
      } else {
        const data = await request<{ itens: ItinerarySummary[] }>(`/roteiros?deslocamento=${offset}`, { token });
        if (version === generation.current) { setItems(previous => offset === 0 ? data.itens : [...previous, ...data.itens.filter(item => !previous.some(p => p.id === item.id))]); setLoadedToken(token); setMore(data.itens.length === 20); }
      }
    } catch (e) {
      if (version !== generation.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível carregar os roteiros.');
    } finally { if (version === generation.current) setBusy(false); }
  }, [token, id, expire]);
  useFocusEffect(useCallback(() => { void load(); return () => { generation.current++; }; }, [load]));
  if (!session && !demo) return <Redirect href="/auth" />;
  // Never render a previous session's private data during a login switch or reload.
  const items = loadedToken === token ? storedItems : [];
  const detail = loadedToken === token ? storedDetail : null;
  function back() { if (router.canGoBack()) router.back(); else router.replace('/account'); }
  const header = detail?.roteiro;
  const timeline = detail && schedule(detail.paradas.map(stop => ({ role: { id: stop.roleId, nome: stop.nome, valor: stop.precoCentavos / 100, local: stop.local, cidade: detail.roteiro.cidade, data: detail.roteiro.data, descricao: null, categoria: null }, minutes: stop.minutos })), detail.roteiro.partida);
  return <ScrollView contentContainerStyle={s.content}>
    <Button title="← Voltar" secondary onPress={back} /><Text style={s.title}>{id === undefined ? 'Meus roteiros' : header?.nome ?? 'Roteiro salvo'}</Text>
    {demo && <Text style={s.subtitle}>A demonstração não salva roteiros. Entre na sua conta para usar esse recurso.</Text>}
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando roteiros" />}<Message text={error} />
    {!!error && <Button title="Tentar novamente" onPress={() => { void load(); }} />}
    {!demo && !busy && !error && id === undefined && items.length === 0 && <Text style={s.subtitle}>Você ainda não salvou roteiros. Monte um na aba Criar rolê.</Text>}
    {id === undefined && items.map(item => <View key={item.id} style={[s.secondary, { borderRadius: 14, padding: 16, gap: 12 }]}><Text style={s.cardTitle}>{item.nome}</Text><Text style={s.subtitle}>{item.data.split('-').reverse().join('/')} • {item.pessoas} pessoa(s) • {price(item.totalCentavos / 100)}</Text><Button title={`Abrir ${item.nome}`} onPress={() => router.push({ pathname: '/itineraries/[id]', params: { id: item.id } })} /></View>)}
    {id === undefined && loadedToken === token && more && !error && <Button title="Ver mais roteiros" busy={busy} onPress={() => { void load(items.length); }} />}
    {header && !error && <>
      <Text style={s.subtitle}>{header.cidade} • {header.data.split('-').reverse().join('/')} • {header.pessoas} pessoa(s)</Text>
      <Text style={s.text}>Total registrado: {price(header.totalCentavos / 100)}</Text><Text style={s.subtitle}>Por pessoa: {price(header.totalCentavos / header.pessoas / 100)} • Limite do grupo: {price(header.orcamentoCentavos / 100)}</Text>
      <Text style={s.small}>Valores registrados ao salvar. Confirme preços, funcionamento e disponibilidade antes de sair. Os horários são estimativas, sem deslocamento.</Text>
      {timeline?.map((stop, index) => <View key={stop.role.id} style={[s.secondary, { borderRadius: 14, padding: 16, gap: 10 }]}><Text style={s.cardTitle}>{index + 1}. {stop.role.nome}</Text><Text style={s.subtitle}>{stop.from} → {stop.to} • {stop.minutes} min</Text><Text style={s.text}>{stop.role.local ?? 'Local não informado'}</Text><Text style={s.small}>{price(stop.role.valor)} por pessoa no momento do salvamento</Text><Button title={`Consultar ${stop.role.nome}`} secondary onPress={() => router.push({ pathname: '/roles/[id]', params: { id: stop.role.id } })} /></View>)}
    </>}
  </ScrollView>;
}
