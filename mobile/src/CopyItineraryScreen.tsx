import { useRef, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { router } from 'expo-router';
import { useAuth } from './AuthContext';
import { ApiError, request, type Role, type SavedItinerary } from './api';
import { useSocialResource } from './useSocialResource';
import type { PublicPublication } from './publication';
import { copyInput, type CopyStop } from './copyItinerary';
import { formatDateInput, formatTimeInput, type Plan } from './itinerary';
import { Button, Field, Message } from './ui';
import { styles as s } from './theme';
import { FormKeyboardView } from './FormKeyboardView';
import { useFormKeyboard } from './useFormKeyboard';

export function CopyItinerary({ token, id }: { token: string; id: string }) {
  const remote = useSocialResource<PublicPublication>(`/publicacoes/${id}`, token, false);
  return remote.data ? <CopyForm key={remote.data.id} token={token} original={remote.data} /> : <View style={s.content}><Text style={s.title}>Copiar rolê</Text><Message text={remote.error} /><Button title="Carregar publicação" secondary busy={remote.loading} onPress={remote.reload} /></View>;
}
function CopyForm({ token, original }: { token: string; original: PublicPublication }) {
  const [plan, setPlan] = useState<Plan>({ name: `Minha versão de ${original.titulo}`.slice(0, 200), city: original.cidade, date: '', start: '10:00', hours: String(Math.min(12, Math.max(1, Math.ceil(original.paradas.reduce((s, p) => s + p.minutos, 0) / 60)))), people: '1', budget: '', budgetMode: 'group' });
  const [stops, setStops] = useState<CopyStop[]>(original.paradas.map(p => ({ roleId: p.roleId, nome: p.nome, minutos: p.minutos })));
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [add, setAdd] = useState(false), [saved, setSaved] = useState<number | null>(null);
  const pending = useRef(false), scroll = useRef<ScrollView>(null), focus = useFormKeyboard(scroll), { expire } = useAuth();
  const current = useSocialResource<PublicPublication>(`/publicacoes/${original.id}`, token, false);
  const set = (key: keyof Plan, value: string) => { if (!pending.current) setPlan(p => ({ ...p, [key]: value })); };
  async function save() {
    if (pending.current || saved) return;
    pending.current = true; setBusy(true); setError(''); const version = current.generation.current;
    try {
      const body = copyInput(plan, stops);
      const result = await request<SavedItinerary>(`/comunidade/copiar/${original.id}`, { method: 'POST', token, body });
      if (version === current.generation.current) setSaved(result.roteiro.id);
    } catch (e) {
      if (version === current.generation.current) { if (e instanceof ApiError && e.status === 401) void expire(); else setError(e instanceof Error ? e.message : 'Não foi possível copiar.'); }
    } finally { pending.current = false; setBusy(false); }
  }
  if (saved) return <View style={s.content}><Text style={s.title}>Sua cópia está salva</Text><Text style={s.text}>O roteiro original foi preservado. Sua versão está em Meus rolês, com os créditos do autor.</Text><Button title="Abrir meu roteiro" onPress={() => router.replace({ pathname: '/itineraries/[id]', params: { id: saved } })} /></View>;
  return <FormKeyboardView><ScrollView ref={scroll} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>Sua versão do passeio</Text><Text style={s.small}>Inspirado em {original.autor.nome}. Escolha uma nova data e orçamento. Preços, aprovação e horários serão conferidos ao salvar; vagas não são garantidas.</Text>
    <Message text={error || current.error} />
    <Field label="Nome" value={plan.name ?? ''} setValue={v => set('name', v)} onFocus={focus} />
    <Field label="Data" value={plan.date} setValue={v => set('date', formatDateInput(v))} keyboardType="number-pad" onFocus={focus} />
    <Field label="Partida" value={plan.start} setValue={v => set('start', formatTimeInput(v))} keyboardType="number-pad" onFocus={focus} />
    <Field label="Duração total em horas (1 a 12)" value={plan.hours} setValue={v => set('hours', v)} keyboardType="number-pad" onFocus={focus} />
    <Field label="Participantes" value={plan.people} setValue={v => set('people', v)} keyboardType="number-pad" onFocus={focus} />
    <Field label="Orçamento total do grupo (R$)" value={plan.budget} setValue={v => set('budget', v)} keyboardType="decimal-pad" onFocus={focus} />
    <Text style={s.cardTitle}>Personalize as paradas</Text>
    {stops.map((p, index) => <View key={p.roleId} style={[s.secondary, { padding: 14, borderRadius: 16, gap: 10 }]}>
      <Text style={s.label}>{index + 1}. {p.nome}</Text>
      <Text style={s.small}>Visita estimada: {p.minutos} min</Text>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        {[-15, 15].map(delta => <Button key={delta} title={`${delta > 0 ? '+' : ''}${delta} min`} secondary disabled={busy || p.minutos + delta < 15 || p.minutos + delta > 720} onPress={() => setStops(stops.map(s => s.roleId === p.roleId ? { ...s, minutos: s.minutos + delta } : s))} />)}
      </View>
      <Field label="Horário de visita opcional" value={p.horario ?? ''} setValue={v => { if (!busy) setStops(stops.map(s => s.roleId === p.roleId ? { ...s, horario: formatTimeInput(v) } : s)); }} onFocus={focus} keyboardType="number-pad" />
      {index > 0 && <Button title="Subir parada" secondary disabled={busy} onPress={() => { const next = [...stops]; [next[index - 1], next[index]] = [next[index], next[index - 1]]; setStops(next); }} />}
      <Button title="Remover parada" secondary disabled={busy} onPress={() => setStops(stops.filter(s => s.roleId !== p.roleId))} />
      <Button title="Consultar local e preço atual" secondary disabled={busy} onPress={() => router.push({ pathname: '/roles/[id]', params: { id: p.roleId } })} />
    </View>)}
    <Button title={add ? 'Fechar opções de paradas' : 'Adicionar outra parada'} secondary disabled={busy} onPress={() => setAdd(!add)} />
    {add && <AdditionalStops token={token} city={plan.city} ids={stops.map(p => p.roleId)} onAdd={role => { if (!busy) setStops([...stops, { roleId: role.id, nome: role.nome, minutos: 60 }]); }} />}
    <Text style={s.small}>Mantenha ao menos uma parada original. Deslocamentos não estão incluídos na duração estimada. Revise incompatibilidades informadas pelo servidor antes de confirmar.</Text>
    <Button title="Verificar e salvar minha cópia" busy={busy} disabled={!current.data || current.loading} onPress={() => { void save(); }} />
  </ScrollView></FormKeyboardView>;
}
function AdditionalStops({ token, city, ids, onAdd }: { token: string; city: string; ids: number[]; onAdd: (r: Role) => void }) {
  const [offset, setOffset] = useState(0);
  const remote = useSocialResource<{ itens: Role[] }>(`/roles?cidade=${encodeURIComponent(city)}&limite=20&deslocamento=${offset}`, token);
  return <View style={{ gap: 10 }}><Message text={remote.error} />{remote.data?.itens.filter(r => !r.cancelado && !ids.includes(r.id)).map(role => <Button key={role.id} title={`Adicionar ${role.nome}`} secondary onPress={() => onAdd(role)} />)}
    {remote.data?.itens.length === 20 && <Button title="Mais opções" secondary onPress={() => setOffset(offset + 20)} />}
    {offset > 0 && <Button title="Opções anteriores" secondary onPress={() => setOffset(Math.max(0, offset - 20))} />}
  </View>;
}
