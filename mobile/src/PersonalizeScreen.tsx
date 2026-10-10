import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { router } from 'expo-router';
import { ApiError, request, type Role, type ItinerarySummary, type SavedItinerary } from './api';
import { useAuth } from './AuthContext';
import { demoRoles } from './demo';
import { Button, DemoBanner, Field, Message, price } from './ui';
import { styles as s } from './theme';
import { candidates, formatDateInput, groupBudgetCents, isoDate, moneyCents, planProblem, schedule, totalCents, validatePlan, type Plan, type Stop } from './itinerary';

export function PersonalizeScreen() {
  const { demo, session, expire } = useAuth();
  const [stage, setStage] = useState<1 | 2 | 3>(1);
  const [plan, setPlan] = useState<Plan>({ city: '', date: '', start: '10:00', hours: '4', people: '1', budget: '', budgetMode: 'group' });
  const [categories, setCategories] = useState<string[]>([]);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const [items, setItems] = useState<Role[]>([]); const [stops, setStops] = useState<Stop[]>([]);
  const [limited, setLimited] = useState(false);
  const [offset, setOffset] = useState(0);
  const searching = useRef(false); const searchGeneration = useRef(0);
  const [saving, setSaving] = useState(false); const [saved, setSaved] = useState<ItinerarySummary | null>(null);
  const generation = useRef(0); const scroll = useRef<ScrollView>(null);
  useEffect(() => () => { generation.current++; searchGeneration.current++; }, []);
  function cancelSearch() { searchGeneration.current++; searching.current = false; setBusy(false); }
  function invalidateSave() { generation.current++; setSaving(false); setSaved(null); }
  function field(name: keyof Plan, value: string) {
    invalidateSave(); cancelSearch(); setError(''); setPlan(previous => ({ ...previous, [name]: value }));
  }
  function navigate(next: 1 | 2 | 3) { setStage(next); scroll.current?.scrollTo({ y: 0, animated: false }); }
  function next() { const issue = validatePlan(plan, 1); if (issue) setError(issue); else { setError(''); navigate(2); } }
  async function search(append = false) {
    if (searching.current) return;
    const issue = validatePlan(plan, 1) || validatePlan(plan, 2);
    if (issue) { setError(issue); return; }
    if (categories.length === 0) { setError('Escolha pelo menos uma categoria.'); return; }
    if (!append) invalidateSave();
    const version = ++searchGeneration.current; searching.current = true; setBusy(true); setError('');
    try {
      const pageOffset = append ? offset : 0;
      const query = new URLSearchParams({ cidade: plan.city.trim(), data: isoDate(plan.date.trim())!, valorMaximo: String(Math.floor(groupBudgetCents(plan)! / Number(plan.people)) / 100), limite: '100', deslocamento: String(pageOffset) });
      const data = demo ? { itens: demoRoles.filter(role => role.cidade.toLowerCase() === plan.city.trim().toLowerCase()) }
        : await request<{ itens: Role[] }>('/roles?' + query, { token: session?.token });
      const options = candidates(data.itens, categories, isoDate(plan.date.trim())!, Number(plan.people), groupBudgetCents(plan)!);
      if (version === searchGeneration.current) {
        setItems(previous => append ? [...previous, ...options.filter(option => !previous.some(item => item.id === option.id))] : options);
        if (!append) { setStops([]); setSaved(null); navigate(3); }
        setOffset(pageOffset + data.itens.length);
        setLimited(!demo && data.itens.length === 100 && pageOffset < 100000);
      }
    } catch (e) { if (version === searchGeneration.current) setError(e instanceof Error ? e.message : 'Não foi possível buscar.'); }
    finally { if (version === searchGeneration.current) { searching.current = false; setBusy(false); } }
  }
  function updateStops(nextStops: Stop[]) {
    const issue = planProblem(nextStops, Number(plan.people), groupBudgetCents(plan)!, Number(plan.hours) * 60);
    if (issue) { setError(issue); scroll.current?.scrollTo({ y: 0, animated: true }); } else { invalidateSave(); setStops(nextStops); setError(''); }
  }
  async function save() {
    if (!session || demo || saving || stops.length === 0) return;
    const version = ++generation.current; setSaving(true); setError('');
    try {
      const data = await request<SavedItinerary>('/roteiros', { method: 'POST', token: session.token, body: {
        nome: `Rolê em ${plan.city.trim()} — ${plan.date}`, cidade: plan.city.trim(), data: isoDate(plan.date.trim()), partida: plan.start.trim(),
        duracaoMinutos: Number(plan.hours) * 60, pessoas: Number(plan.people), orcamentoCentavos: moneyCents(plan.budget),
        modoOrcamento: plan.budgetMode ?? 'group', paradas: stops.map(stop => ({ roleId: stop.role.id, minutos: stop.minutes })),
      } });
      if (version === generation.current) setSaved(data.roteiro);
    } catch (e) {
      if (version !== generation.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else { setError((e instanceof Error ? e.message : 'Não foi possível salvar.') + (!(e instanceof ApiError) ? ' Confira Meus roteiros antes de tentar salvar novamente.' : '')); scroll.current?.scrollTo({ y: 0, animated: true }); }
    } finally { if (version === generation.current) setSaving(false); }
  }
  function move(index: number, offset: number) {
    const result = [...stops]; const target = index + offset;
    [result[index], result[target]] = [result[target], result[index]]; updateStops(result);
  }
  const total = totalCents(stops, Number(plan.people));
  return <ScrollView ref={scroll} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    {demo && <DemoBanner />}<Text style={s.title}>Criar meu rolê</Text>
    <Text style={s.subtitle}>{stage === 1 ? '1 de 2 • Quando e onde vamos?' : stage === 2 ? '2 de 2 • Como você quer curtir?' : 'Monte seu roteiro'}</Text>
    {stage === 1 && <>
      <Field label="Cidade" value={plan.city} setValue={value => field('city', value)} placeholder="Ex.: Uberaba" />
      <Field label="Data" value={plan.date} setValue={value => field('date', formatDateInput(value))} placeholder="DD/MM/AAAA" keyboardType="number-pad" />
      <Text style={s.small}>Digite apenas os números. As barras são inseridas automaticamente.</Text>
      <Field label="Horário de partida" value={plan.start} setValue={value => field('start', value)} placeholder="HH:MM" keyboardType="numbers-and-punctuation" />
      <Field label="Duração em horas" value={plan.hours} setValue={value => field('hours', value)} keyboardType="number-pad" />
      <Message text={error} /><Button title="Próximo" onPress={next} />
    </>}
    {stage === 2 && <>
      <Field label="Número de pessoas" value={plan.people} setValue={value => field('people', value)} keyboardType="number-pad" />
      <Text style={s.label}>O que vocês querem fazer?</Text><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {['Música', 'Festa', 'Gastronomia', 'Cultura', 'Esporte', 'Natureza', 'Cinema', 'Outros'].map(value => <Pressable key={value} accessibilityRole="button" accessibilityState={{ selected: categories.includes(value) }} style={[s.chip, categories.includes(value) && s.selected]} onPress={() => { invalidateSave(); cancelSearch(); setError(''); setCategories(previous => previous.includes(value) ? previous.filter(c => c !== value) : [...previous, value]); }}><Text style={[s.chipText, categories.includes(value) && s.selectedText]}>{value}</Text></Pressable>)}
      </View>
      <Text style={s.label}>Como deseja definir o orçamento?</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{([{ value: 'person', title: 'Por pessoa' }, { value: 'group', title: 'Total do grupo' }] as const).map(mode => <Pressable key={mode.value} accessibilityRole="button" accessibilityState={{ selected: plan.budgetMode === mode.value }} style={[s.chip, plan.budgetMode === mode.value && s.selected]} onPress={() => { invalidateSave(); cancelSearch(); setError(''); setPlan(previous => ({ ...previous, budgetMode: mode.value })); }}><Text style={[s.chipText, plan.budgetMode === mode.value && s.selectedText]}>{mode.title}</Text></Pressable>)}</View>
      <Field label={plan.budgetMode === 'person' ? 'Quanto cada pessoa quer gastar (R$)' : 'Orçamento total do grupo (R$)'} value={plan.budget} setValue={value => field('budget', value)} keyboardType="decimal-pad" placeholder="Ex.: 50,00" />
      {groupBudgetCents(plan) !== null && <Text style={s.subtitle}>Limite total do grupo: {price(groupBudgetCents(plan)! / 100)}{plan.budgetMode === 'person' ? ` (${plan.people} pessoa(s) com o mesmo limite)` : ''}</Text>}
      <Text style={s.small}>O cálculo considera o preço cadastrado por pessoa. Alimentação extra e transporte não estão incluídos.</Text>
      <Text style={s.small}>{stops.length > 0 ? 'Uma nova busca substitui as paradas atuais.' : ''}</Text><Message text={error} /><Button title="Buscar opções" onPress={() => { void search(); }} busy={busy} />
      <Button title="Voltar" secondary onPress={() => { invalidateSave(); cancelSearch(); setError(''); navigate(1); }} />
    </>}
    {stage === 3 && <>
      <Text style={s.subtitle}>{plan.city.trim()} • {plan.date} • {plan.people} pessoa(s)</Text>
      <Text style={s.small}>Escolha a ordem das paradas. Os horários abaixo são estimativas: confirme disponibilidade, funcionamento e deslocamento antes de sair.</Text>
      <View style={[s.secondary, { borderRadius: 14, padding: 16, gap: 8 }]}>
        <Text style={s.label}>Meu roteiro • {stops.length} parada(s)</Text>
        <Text style={s.text}>Total previsto: {price(total / 100)}</Text>
        <Text style={s.subtitle}>Por pessoa: {price(total / Number(plan.people) / 100)} • Limite do grupo: {price((groupBudgetCents(plan) ?? 0) / 100)}</Text>
        <Text style={s.subtitle}>Saldo: {price(((groupBudgetCents(plan) ?? 0) - total) / 100)} • {stops.reduce((sum, stop) => sum + stop.minutes, 0)} de {Number(plan.hours) * 60} min</Text>
        {stops.length === 0 && <Text style={s.small}>Adicione uma opção abaixo para começar.</Text>}
      </View>
      <Message text={error} />
      {demo ? <Text style={s.small}>Roteiro temporário da demonstração. Entre na sua conta para salvar.</Text> : <>
        {saved ? <><Text style={s.subtitle}>Salvo na conta com os preços atuais: {price(saved.totalCentavos / 100)} para o grupo.</Text><Button title="Abrir roteiro salvo" onPress={() => router.push({ pathname: '/itineraries/[id]', params: { id: saved.id } })} /></> : <Button title="Salvar roteiro na conta" onPress={save} busy={saving} disabled={stops.length === 0} />}
        <Button title="Meus roteiros" secondary onPress={() => router.push('/itineraries')} />
      </>}
      {schedule(stops, plan.start).map((stop, index) => <View key={stop.role.id} style={[s.secondary, { borderRadius: 14, padding: 16, gap: 10 }]}>
        <Text style={s.cardTitle}>{index + 1}. {stop.role.nome}</Text><Text style={s.subtitle}>{stop.from} → {stop.to} • {price(Math.round(stop.role.valor * 100) * Number(plan.people) / 100)} para o grupo</Text>
        <Text style={s.small}>{stop.minutes} min estimados • ajuste o tempo da visita</Text>
        <View style={s.row}><Button title="−15 min" secondary onPress={() => { if (stop.minutes > 15) updateStops(stops.map((value, i) => i === index ? { ...value, minutes: value.minutes - 15 } : value)); }} /><Button title="+15 min" secondary onPress={() => updateStops(stops.map((value, i) => i === index ? { ...value, minutes: value.minutes + 15 } : value))} /></View>
        <View style={s.row}>{index > 0 && <Button title="Subir" secondary onPress={() => move(index, -1)} />}{index < stops.length - 1 && <Button title="Descer" secondary onPress={() => move(index, 1)} />}</View>
        <Button title={'Remover ' + stop.role.nome} secondary onPress={() => updateStops(stops.filter(value => value.role.id !== stop.role.id))} />
      </View>)}
      <Text style={s.label}>Opções para adicionar</Text>{limited && <Text style={s.small}>Há mais opções para consultar. Carregá-las mantém as paradas que você já escolheu.</Text>}
      {items.length === 0 && <Text style={s.subtitle}>{limited ? 'Nenhuma opção das categorias escolhidas nesta página. Carregue mais opções para continuar a busca.' : 'Nenhuma opção encontrada para a data, categorias e orçamento escolhidos.'}</Text>}
      {items.map(role => <View key={role.id} style={[s.secondary, { borderRadius: 14, padding: 16, gap: 10 }]}><Text style={s.cardTitle}>{role.nome}</Text><Text style={s.subtitle}>{role.categoria} • {price(role.valor)} por pessoa</Text>{!role.data && <Text style={s.small}>Data e disponibilidade a confirmar com o local.</Text>}
        {stops.some(stop => stop.role.id === role.id) ? <Text style={s.small}>Adicionado ao roteiro</Text> : <Button title={'Adicionar ' + role.nome} onPress={() => updateStops([...stops, { role, minutes: 60 }])} />}
        <Button title={'Detalhes de ' + role.nome} secondary onPress={() => router.push({ pathname: '/roles/[id]', params: { id: role.id } })} />
      </View>)}
      {limited && <Button title="Carregar mais opções" busy={busy} disabled={saving} onPress={() => { void search(true); }} />}
      <Button title="Revisar preferências" secondary onPress={() => { cancelSearch(); setError(''); navigate(2); }} />
      <Text style={s.small}>{demo ? 'A demonstração não grava dados na conta.' : 'As alterações ficam nesta sessão até você salvar. Alterar um roteiro salvo e salvar novamente cria outro registro.'}</Text>
    </>}
  </ScrollView>;
}
