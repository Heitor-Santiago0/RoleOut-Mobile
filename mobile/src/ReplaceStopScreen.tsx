import { useEffect, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useAuth } from './AuthContext';
import { useRoutingPlan } from './RoutingPlanContext';
import { roles, type Role } from './api';
import { demoRoles } from './demo';
import { replaceStop, replacementProblem } from './itinerary';
import { Button, DemoBanner, Message, price } from './ui';
import { colors, styles as s } from './theme';

export default function ReplaceStopScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { session, demo } = useAuth();
  const { stops } = useRoutingPlan();
  const target = stops.find(stop => stop.role.id === Number(id));
  if (!session && !demo) return <Redirect href="/auth" />;
  if (!target) return <View style={s.content}><Text style={s.subtitle}>Essa parada não está mais no roteiro.</Text><Button title="Ver Organizar rolê" onPress={() => router.replace('/organize')} /></View>;
  return <Alternatives key={`${session?.token ?? 'demo'}:${target.role.id}`} original={target.role} demo={demo} token={session?.token} />;
}

function Alternatives({ original, demo, token }: { original: Role; demo: boolean; token?: string }) {
  const { stops, plan, setStops, setSaved } = useRoutingPlan();
  const [items, setItems] = useState<Role[]>([]);
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [more, setMore] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const result = demo ? Promise.resolve({ itens: demoRoles }) : roles(original.cidade, original.categoria || 'Todos', page * 20, token);
    result.then(data => {
      if (!active) return;
      setItems(previous => page === 0 ? data.itens : [...previous, ...data.itens.filter(role => !previous.some(item => item.id === role.id))]);
      setMore(!demo && data.itens.length === 20);
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Não foi possível buscar alternativas.'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [demo, token, original.cidade, original.categoria, page, retry]);
  const options = items.filter(role => !replacementProblem(stops, original.id, role, plan));
  return <ScrollView contentContainerStyle={s.content}>
    <Text accessibilityRole="header" style={s.title}>Trocar parada</Text>
    {demo && <DemoBanner />}
    <Text style={s.subtitle}>No lugar de: {original.nome}</Text>
    <Text style={s.text}>{original.categoria ? `Outras opções de ${original.categoria} em ${original.cidade}.` : `Outras opções em ${original.cidade}.`} A posição e o tempo de visita serão mantidos.</Text>
    {!!plan.date && <Text style={s.small}>Compatíveis com a data escolhida: {plan.date}.</Text>}
    <Text style={s.small}>Confira o valor antes de escolher. Não temos informações de lotação ou disponibilidade em tempo real.</Text>
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Buscando alternativas" />}
    <Message text={error} />
    {!!error && <Button title="Tentar novamente" onPress={() => { setError(''); setBusy(true); setRetry(value => value + 1); }} />}
    {!busy && !error && options.length === 0 && <Text style={s.subtitle}>{more ? 'Nenhuma alternativa compatível nesta página. Busque mais opções abaixo.' : 'Nenhuma alternativa compatível encontrada no catálogo consultado. Sua parada atual foi mantida.'}</Text>}
    {options.map(role => <View key={role.id} style={[s.card, { flex: 0 }]}><View style={s.cardBody}>
      <Text style={s.cardTitle}>{role.nome}</Text>
      <Text style={s.small}>{role.local || 'Local não informado'} · {role.cidade}</Text>
      <Text style={s.text}>{price(role.valor)} por pessoa</Text>
      <Text style={s.text}>{role.descricao || 'Descrição não informada.'}</Text>
      {!!role.data && <Text style={s.small}>{role.data.split('-').reverse().join('/')}</Text>}
      <Button title={`Escolher ${role.nome}`} disabled={busy || !!error} onPress={() => {
        try {
          const next = replaceStop(stops, original.id, role, plan);
          setStops(next); setSaved(null);
          router.dismissTo('/organize');
        } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível trocar a parada.'); }
      }} />
    </View></View>)}
    {more && !error && <Button title="Buscar mais opções" busy={busy} onPress={() => { setBusy(true); setPage(value => value + 1); }} />}
    <Button title="Manter parada atual" secondary onPress={() => router.dismissTo('/organize')} />
  </ScrollView>;
}
