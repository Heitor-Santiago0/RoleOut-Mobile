import { useCallback, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { ApiError, request, type ItinerarySummary } from './api';
import { useAuth } from './AuthContext';
import { featuredItinerary, loadItineraryPages } from './itineraryGroups';
import { itineraryStatus } from './itineraryStatus';
import { Button } from './ui';
import { styles as s } from './theme';

export function UpcomingItineraryCard({ token }: { token: string }) {
  const [attempt, setAttempt] = useState(0);
  return <UpcomingContent key={attempt} token={token} onRetry={() => setAttempt(value => value + 1)} />;
}

function UpcomingContent({ token, onRetry }: { token: string; onRetry: () => void }) {
  const { expire } = useAuth();
  const [items, setItems] = useState<ItinerarySummary[]>([]);
  const [busy, setBusy] = useState(true); const [error, setError] = useState('');
  const [now, setNow] = useState(() => new Date());
  useFocusEffect(useCallback(() => {
    let active = true;
    setBusy(true); setError(''); setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 30000);
    loadItineraryPages(async offset => {
      if (!active) throw new Error('Consulta encerrada.');
      return (await request<{ itens: ItinerarySummary[] }>(`/roteiros?deslocamento=${offset}`, { token })).itens;
    }).then(data => { if (active) setItems(data); }).catch(e => {
      if (!active) return;
      if (e instanceof ApiError && e.status === 401) void expire();
      else setError('Não foi possível consultar seu próximo rolê.');
    }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; clearInterval(timer); };
  }, [token, expire]));
  if (busy) return <Text style={s.small}>Consultando seus rolês salvos…</Text>;
  if (error) return <View style={{ gap: 8 }}><Text style={s.small}>{error}</Text><Button title="Atualizar próximo rolê" secondary onPress={onRetry} /></View>;
  const item = featuredItinerary(items, now);
  if (!item) return null;
  const status = itineraryStatus(item, now);
  return <View style={[s.secondary, { borderRadius: 14, padding: 16, gap: 10 }]}>
    <Text accessibilityRole="header" style={s.label}>{(item.paradasConcluidas ?? 0) > 0 ? 'Retome seu rolê' : 'Seu próximo rolê'}</Text>

    <Text style={s.cardTitle}>{item.nome}</Text>
    <Text style={s.small}>{item.data.split('-').reverse().join('/')} · {item.partida} · {status.label}</Text>

    <Button title={status.action} onPress={() => router.push({ pathname: '/itineraries/[id]', params: { id: item.id } })} />
    <Pressable accessibilityRole="button" onPress={() => router.push('/itineraries')} style={{ minHeight: 44, justifyContent: 'center' }}><Text style={{ color: '#9DBFFF' }}>Meus rolês →</Text></Pressable>
  </View>;
}
