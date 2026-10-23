import { useEffect, useRef, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { Redirect, router } from 'expo-router';
import { useAuth } from './AuthContext';
import { useRoutingPlan } from './RoutingPlanContext';
import { SavedRouteButton } from './SavedRouteButton';
import { Button, DemoBanner, Field, Message, price } from './ui';
import { colors, styles as s } from './theme';
import { ApiError, request, type SavedItinerary } from './api';
import { formatDateInput, formatTimeInput, type Plan } from './itinerary';
import { useFormKeyboard } from './useFormKeyboard';
import { FormKeyboardView } from './FormKeyboardView';
import { SmartOrderControl } from './SmartOrderControl';
import { localDeparture, organizeSchedule } from './itinerary';

export default function OrganizeScreen() {
  const { session, demo, expire } = useAuth();
  const { stops, setStops, saved, setSaved, plan, setPlan, startNew } = useRoutingPlan();
  const scroll = useRef<ScrollView>(null);
  const focusField = useFormKeyboard(scroll);
  const [error, setError] = useState('');
  const [scheduling, setScheduling] = useState(false);
  const [date, setDate] = useState(plan.date);
  const [time, setTime] = useState(plan.start);
  const [saving, setSaving] = useState(false);
  const [ordering, setOrdering] = useState(false);
  const submitting = useRef(false);
  const active = useRef(true);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  const confirmed = saved?.signature === JSON.stringify({ plan, stops }) ? saved.summary : null;
  const mixedCities = new Set(stops.map(stop => stop.role.cidade.trim().toLocaleLowerCase('pt-BR'))).size > 1;
  async function save(goNow: boolean) {
    if (!session || submitting.current || ordering) return;
    submitting.current = true; setSaving(true); setError('');
    try {
      const now = new Date();
      const departure = goNow ? localDeparture(now) : { date, time };
      const body = organizeSchedule(stops, departure.date, departure.time, now, !goNow, plan.name);
      const result = await request<SavedItinerary>('/roteiros', { method: 'POST', token: session.token, body });
      if (!active.current) return;
      const nextPlan: Plan = { name: plan.name, city: body.cidade, date: departure.date, start: departure.time, hours: String(Math.ceil(body.duracaoMinutos / 60)), people: '1', budget: (body.orcamentoCentavos / 100).toFixed(2), budgetMode: 'person' };
      setPlan(nextPlan); setSaved({ summary: result.roteiro, signature: JSON.stringify({ plan: nextPlan, stops }) });
      setScheduling(false);
    } catch (e) {
      if (!active.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível salvar. Tente novamente.');
    } finally { submitting.current = false; if (active.current) setSaving(false); }
  }
  if (!session && !demo) return <Redirect href="/auth" />;
  function move(index: number, offset: number) {
    setSaved(null); setError('');
    setStops(previous => {
      const next = [...previous];
      const target = index + offset;
      if (target < 0 || target >= next.length) return previous;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }
  return <FormKeyboardView><ScrollView ref={scroll} contentContainerStyle={[s.content, { paddingBottom: 120 }]} keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag">
    <Text accessibilityRole="header" style={s.title}>Organizar rolê</Text>
    {demo && <DemoBanner />}
    <Text style={s.subtitle}>Suas paradas, na ordem em que você quer visitar.</Text>
    {!confirmed && stops.length > 0 && <Field label="Nome do rolê (opcional)" value={plan.name ?? ''} placeholder="Ex.: Sábado com amigos" onFocus={focusField} setValue={name => { if (!submitting.current) { setPlan(previous => ({ ...previous, name })); setSaved(null); setError(''); } }} />}
    {confirmed && <Text style={s.cardTitle}>{confirmed.nome}</Text>}
    {mixedCities && <Message text="Este roteiro contém paradas de cidades diferentes. Remova a parada da outra cidade antes de continuar. Nenhuma parada será removida automaticamente." />}
    {confirmed && <Text style={s.small}>Trocar uma parada altera esta montagem. Para guardar a nova versão, salve novamente; o roteiro anterior permanece em Meus rolês.</Text>}
    <SmartOrderControl date={scheduling ? date : plan.date || localDeparture(new Date()).date} start={scheduling ? time : plan.date ? plan.start : localDeparture(new Date()).time} disabled={saving || !!confirmed || mixedCities} onBusyChange={setOrdering} />
    {stops.length === 0 ? <Text style={s.text}>Nenhum rolê adicionado ainda. Escolha uma parada para começar.</Text> :
      <View style={table.frame}>
        <View style={[table.row, table.header]}><Text style={[s.label, table.order]}>Nº</Text><Text style={[s.label, table.details]}>Rolê e lugar</Text></View>
        {stops.map((stop, index) => <View key={stop.role.id} style={table.row}>
          <Text style={[s.text, table.order]}>{index + 1}</Text>
          <View style={[table.details, { gap: 8 }]}>
            <Text style={s.cardTitle}>{stop.role.nome}</Text>
            <Text style={s.small}>{stop.role.local || 'Local não informado'} · {stop.role.cidade}</Text>
            <Text style={s.small}>{stop.role.categoria || 'Sem categoria'} · {price(stop.role.valor)} por pessoa</Text>
            <Text numberOfLines={3} style={s.text}>{stop.role.descricao || 'Descrição ainda não informada.'}</Text>
            {!!stop.role.data && <Text style={s.small}>{stop.role.data.split('-').reverse().join('/')}</Text>}
            {!!stop.role.horario && <Text style={s.label}>Início publicado: {stop.role.horario}</Text>}
            <Text style={s.small}>Término publicado: {stop.role.horarioFim ?? 'não informado'}{stop.role.dataFim ? ` em ${stop.role.dataFim.split('-').reverse().join('/')}` : ''}</Text>
            <Text style={s.small}>Duração estimada da visita: {stop.minutes} minutos</Text>
            {!confirmed && <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {([-15, 15] as const).map(delta => <Button key={delta} title={delta < 0 ? 'Menos 15 min' : 'Mais 15 min'} secondary disabled={saving || stop.minutes + delta < 15 || stop.minutes + delta > 720} onPress={() => { setStops(previous => previous.map(item => item.role.id === stop.role.id ? { ...item, minutes: item.minutes + delta } : item)); setSaved(null); setError(''); }} />)}
            </View>}
            {!confirmed && <Field label="Seu horário de visita (opcional)" value={stop.horario ?? ''} placeholder="HH:MM" keyboardType="number-pad" onFocus={focusField} setValue={value => { setStops(previous => previous.map(item => item.role.id === stop.role.id ? { ...item, horario: formatTimeInput(value) } : item)); setSaved(null); }} />}
            <Button title="Ver detalhes" secondary disabled={saving} onPress={() => router.push({ pathname: '/roles/[id]', params: { id: stop.role.id } })} />
            <Button title="Trocar parada" secondary disabled={saving} onPress={() => router.push({ pathname: '/replace-stop/[id]', params: { id: stop.role.id } })} />
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {index > 0 && <Button title="Subir" secondary disabled={saving} onPress={() => move(index, -1)} />}
              {index < stops.length - 1 && <Button title="Descer" secondary disabled={saving} onPress={() => move(index, 1)} />}
              <Button title="Remover" secondary disabled={saving} onPress={() => { const remaining = stops.filter(item => item.role.id !== stop.role.id); setStops(remaining); setPlan(previous => ({ ...previous, city: remaining[0]?.role.cidade ?? '' })); setSaved(null); setError(''); }} />
            </View>
          </View>
        </View>)}
      </View>}
    <Button title="Adicionar mais uma parada neste rolê" disabled={saving} onPress={() => { setSaved(null); router.dismissTo('/explore'); }} />
    {stops.length > 0 && !confirmed && <>
      <Text style={s.label}>Quando você quer ir?</Text>
      <Button title="Ir agora" busy={saving} disabled={demo || ordering} onPress={() => { void save(true); }} />
      <Button title="Escolher data e horário" secondary disabled={saving} onPress={() => { setScheduling(true); setError(''); }} />
      {scheduling && <View style={{ gap: 12 }}>
        <Field label="Data" value={date} setValue={value => setDate(formatDateInput(value))} placeholder="DD/MM/AAAA" keyboardType="number-pad" onFocus={focusField} />
        <Field label="Horário" value={time} setValue={value => setTime(formatTimeInput(value))} placeholder="HH:MM" keyboardType="number-pad" onFocus={focusField} />
        <Button title="Salvar para esse horário" busy={saving} disabled={demo || ordering} onPress={() => { void save(false); }} />
      </View>}
      <Text style={s.small}>Salva as paradas na sua conta. Valores por pessoa; duração estimada conforme as visitas e a espera por horários marcados. Deslocamentos não estão incluídos.</Text>
      {demo && <Text style={s.small}>Entre na sua conta para salvar.</Text>}
    </>}
    {confirmed && <>
    <Text style={s.subtitle}>Rolê salvo para {confirmed.data.split('-').reverse().join('/')} às {confirmed.partida}.</Text>
    <Button title="Criar outro rolê" onPress={() => { startNew(); router.navigate('/personalize'); }} />
    <SavedRouteButton key={confirmed.id} id={confirmed.id} />
    <Button title="Acompanhar paradas" secondary onPress={() => router.push({ pathname: '/itineraries/[id]', params: { id: confirmed.id } })} />
    </>}
    <Message text={error} />
  </ScrollView></FormKeyboardView>;
}

const table = StyleSheet.create({
  frame: { borderWidth: 1, borderColor: colors.border, borderRadius: 14, overflow: 'hidden' },
  row: { flexDirection: 'row', padding: 12, borderBottomWidth: 1, borderBottomColor: colors.border, backgroundColor: colors.card },
  header: { backgroundColor: '#29324B' },
  order: { width: 32 },
  details: { flex: 1, minWidth: 0 },
});
