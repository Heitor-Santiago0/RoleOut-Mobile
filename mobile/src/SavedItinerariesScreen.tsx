import { ScheduleAlerts } from './ScheduleAlerts';
import { Fragment, useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Text, View } from 'react-native';
import { Redirect, router, useFocusEffect } from 'expo-router';
import { ApiError, request, type ItinerarySummary, type SavedItinerary } from './api';
import { useAuth } from './AuthContext';
import { Button, Field, Message, price } from './ui';
import { colors, styles as s } from './theme';
import { schedule } from './itinerary';
import { DeleteItinerary } from './DeleteItinerary';
import { FormKeyboardView } from './FormKeyboardView';
import { useFormKeyboard } from './useFormKeyboard';
import { useRoutingPlan } from './RoutingPlanContext';
import { DirectionsButton } from './DirectionsButton';
import { itineraryStatus } from './itineraryStatus';
import { offlineItineraries } from './offlineItineraries';
import { OfflineItinerariesScreen } from './OfflineItinerariesScreen';
import { groupItineraries, loadItineraryPages, searchItineraries, type ItineraryFilter } from './itineraryGroups';

export function SavedItinerariesScreen({ id }: { id?: number }) {
  const { session, demo, expire } = useAuth(); const token = session?.token;
  const { plan, stops, saved, setPlan, setSaved } = useRoutingPlan();
  const scroll = useRef<ScrollView>(null);
  const focusField = useFormKeyboard(scroll);
  const [editingName, setEditingName] = useState(false);
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const renaming = useRef(false);
  const [storedItems, setItems] = useState<ItinerarySummary[]>([]); const [storedDetail, setDetail] = useState<SavedItinerary | null>(null);
  const [loadedToken, setLoadedToken] = useState<string>();
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [filter, setFilter] = useState<ItineraryFilter>('active');
  const [query, setQuery] = useState('');
  const generation = useRef(0);
  const [now, setNow] = useState(() => new Date());
  const [showOffline, setShowOffline] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const downloadLock = useRef(false);
  const [offlineMessage, setOfflineMessage] = useState('');
  const [offlineCopies, setOfflineCopies] = useState<{ token: string; dates: Record<number, string> } | null>(null);
  const refreshCopies = useCallback(async (version: number) => {
    if (!token) return;
    try {
      const copies = await offlineItineraries.read(token);
      if (version === generation.current) setOfflineCopies({ token, dates: Object.fromEntries(copies.map(copy => [copy.data.roteiro.id, copy.savedAt])) });
    } catch { if (version === generation.current) setOfflineCopies(null); }
  }, [token]);
  useFocusEffect(useCallback(() => {
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 30000);
    return () => clearInterval(timer);
  }, []));
  const load = useCallback(async () => {
    if (!token) return;
    const version = ++generation.current; setBusy(true); setError('');
    try {
      if (id !== undefined) {
        if (!Number.isSafeInteger(id) || id < 1) throw new Error('Roteiro inválido.');
        const data = await request<SavedItinerary>(`/roteiros/${id}`, { token });
        if (version === generation.current) { setDetail(data); setLoadedToken(token); }
      } else {
        const data = await loadItineraryPages(async offset => {
          if (version !== generation.current) throw new Error('Consulta substituída.');
          return (await request<{ itens: ItinerarySummary[] }>(`/roteiros?deslocamento=${offset}`, { token })).itens;
        });
        if (version === generation.current) {
          setItems(data); setLoadedToken(token);
          await offlineItineraries.retain(token, data.map(item => item.id)).catch(() => {});
        }
      }
    } catch (e) {
      if (version !== generation.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível carregar os roteiros.');
    } finally {
      await refreshCopies(version);
      if (version === generation.current) setBusy(false);
    }
  }, [token, id, expire, refreshCopies]);
  useFocusEffect(useCallback(() => { void load(); return () => { generation.current++; }; }, [load]));
  async function download(onlyId?: number) {
    if (!token || busy || downloadLock.current || loadedToken !== token) return;
    downloadLock.current = true; setDownloading(true); setOfflineMessage('Baixando roteiros…');
    const version = generation.current;
    const targets = onlyId === undefined ? storedItems.map(item => item.id) : [onlyId];
    let count = 0;
    try {
      for (const targetId of targets) {
        if (version !== generation.current) return;
        const data = await request<SavedItinerary>(`/roteiros/${targetId}`, { token });
        // Explicit writes report storage failures; an API success alone is not a download confirmation.
        await offlineItineraries.save(token, data);
        count++;
        await refreshCopies(version);
        if (version === generation.current) setOfflineMessage(`${count} de ${targets.length} roteiro(s) disponíveis offline.`);
      }
    } catch (e) {
      if (version !== generation.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setOfflineMessage(`Download interrompido. ${count} roteiro(s) baixados nesta tentativa. Confira a conexão e tente novamente.`);
    } finally { await refreshCopies(version); downloadLock.current = false; setDownloading(false); }
  }
  async function rename() {
    if (!token || id === undefined || busy || downloadLock.current || renaming.current) return;
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 200) { setNameError('Informe um nome de 1 a 200 caracteres.'); return; }
    const version = ++generation.current;
    renaming.current = true; setBusy(true); setNameError('');
    try {
      const data = await request<SavedItinerary>(`/roteiros/${id}/nome`, { method: 'PUT', token, body: { nome: trimmed } });
      if (version !== generation.current) return;
      setDetail(data); setLoadedToken(token); setEditingName(false);
      if (saved?.summary.id === id && saved.signature === JSON.stringify({ plan, stops })) {
        const nextPlan = { ...plan, name: data.roteiro.nome };
        setPlan(nextPlan); setSaved({ summary: data.roteiro, signature: JSON.stringify({ plan: nextPlan, stops }) });
      }
    } catch (e) {
      if (version !== generation.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setNameError(e instanceof Error ? e.message : 'Não foi possível salvar o nome. Tente novamente.');
    } finally { await refreshCopies(version); renaming.current = false; if (version === generation.current) setBusy(false); }
  }
  async function progress(roleId: number, concluida: boolean) {
    if (!token || id === undefined || busy || downloadLock.current) return;
    const version = ++generation.current; setBusy(true); setError('');
    try {
      const data = await request<SavedItinerary>(`/roteiros/${id}/paradas/${roleId}`, { method: 'PUT', token, body: { concluida } });
      if (version === generation.current) { setDetail(data); setLoadedToken(token); }
    } catch (e) {
      if (version !== generation.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError('Não foi possível confirmar a atualização. Toque em Tentar novamente para consultar o progresso salvo.');
    } finally { await refreshCopies(version); if (version === generation.current) setBusy(false); }
  }
  if (!session && !demo) return <Redirect href="/auth" />;
  if (showOffline) return <OfflineItinerariesScreen onBack={() => setShowOffline(false)} />;
  // Never render a previous session's private data during a login switch or reload.
  const items = loadedToken === token ? storedItems : [];
  const groups = groupItineraries(searchItineraries(items, query), now, filter);
  const completed = groupItineraries(items, now, 'completed').reduce((sum, group) => sum + group.items.length, 0);
  const detail = loadedToken === token ? storedDetail : null;
  const header = detail?.roteiro;
  const copyDates = offlineCopies?.token === token ? offlineCopies?.dates : undefined;
  function offlineLabel(routeId: number) {
    if (!copyDates) return 'Disponibilidade offline ainda não confirmada.';
    const date = copyDates[routeId];
    return date ? `Cópia offline disponível · ${new Date(date).toLocaleString('pt-BR')}` : 'Ainda não baixado neste aparelho.';
  }
  const nextIndex = detail?.paradas.findIndex(stop => !stop.concluida) ?? -1;
  const completedCount = detail?.paradas.filter(stop => stop.concluida).length ?? 0;
  const timeline = detail && schedule(detail.paradas.map(stop => ({ role: { id: stop.roleId, nome: stop.nome, valor: stop.precoCentavos / 100, local: stop.local, cidade: detail.roteiro.cidade, data: detail.roteiro.data, descricao: null, categoria: null }, minutes: stop.minutos, horario: stop.horario ?? undefined })), detail.roteiro.partida);
  return <FormKeyboardView><ScrollView ref={scroll} contentContainerStyle={[s.content, { paddingBottom: 120 }]} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>{id === undefined ? 'Meus rolês' : header?.nome ?? 'Rolê salvo'}</Text>
    {!demo && token && <ScheduleAlerts key={`${token}:${id ?? "all"}:${detail?.paradas.map(p => p.concluida).join()}`} token={token} id={id} />}
    {!demo && <Button title="Consultar roteiros offline" secondary onPress={() => setShowOffline(true)} />}
    {id === undefined && !demo && <>
      <Button title="Baixar roteiros para usar offline" busy={downloading} disabled={busy || loadedToken !== token || storedItems.length === 0} onPress={() => { void download(); }} />
      <Text style={s.small}>Baixe antes de sair. Roteiros abertos ou salvos também recebem uma cópia quando o armazenamento está disponível. Ao sair da conta, as cópias são removidas.</Text>
    </>}
    {!demo && !!offlineMessage && <Text accessibilityRole="alert" style={s.small}>{offlineMessage}</Text>}
    {header && !demo && <>
      <Text style={s.small}>{offlineLabel(header.id)}</Text>
      <Button title={copyDates?.[header.id] ? 'Atualizar cópia offline' : 'Baixar este roteiro'} secondary busy={downloading} disabled={busy} onPress={() => { void download(header.id); }} />
    </>}
    {id === undefined && !demo && <Text style={s.subtitle}>Veja seus próximos passeios e retome de onde parou.</Text>}
    {id === undefined && !demo && <>
      <Field label="Buscar por nome ou cidade" value={query} setValue={setQuery} placeholder="Ex.: Aniversário ou Uberaba" />
      {!!query && <Button title="Limpar busca" secondary onPress={() => setQuery('')} />}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {([{ key: 'active', title: 'A fazer', count: items.length - completed }, { key: 'completed', title: 'Concluídos', count: completed }, { key: 'all', title: 'Todos', count: items.length }] as const).map(option => <Pressable key={option.key} accessibilityRole="button" accessibilityState={{ selected: filter === option.key }} style={[s.chip, filter === option.key && s.selected]} onPress={() => setFilter(option.key)}><Text style={[s.chipText, filter === option.key && s.selectedText]}>{option.title}{loadedToken === token ? ` (${option.count})` : ''}</Text></Pressable>)}
      </View>
      <Button title="Atualizar meus rolês" secondary busy={busy} disabled={downloading} onPress={() => { void load(); }} />
      {!busy && !error && items.length > 0 && groups.length === 0 && <Text style={s.subtitle}>{query.trim() ? 'Nenhum rolê encontrado neste filtro. Tente outro nome, cidade ou selecione Todos.' : filter === 'completed' ? 'Você ainda não concluiu nenhum rolê.' : 'Todos os seus rolês foram concluídos. Consulte a aba Concluídos ou planeje um novo passeio.'}</Text>}
    </>}
    {demo && <Text style={s.subtitle}>A demonstração não salva roteiros. Entre na sua conta para usar esse recurso.</Text>}
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando roteiros" />}<Message text={error} />
    {!!error && <Button title="Tentar novamente" onPress={() => { void load(); }} />}
    {!demo && !busy && !error && id === undefined && items.length === 0 && <>
      <Text style={s.subtitle}>Você ainda não salvou um rolê. Explore os lugares e adicione sua primeira parada.</Text>
      <Button title="Explorar rolês" onPress={() => router.dismissTo('/explore')} />
    </>}
    {id === undefined && groups.map(group => <Fragment key={group.key}>
      <Text accessibilityRole="header" style={s.cardTitle}>{group.title} · {group.items.length}</Text>
      {group.key === 'pending' && <Text style={s.small}>A data passou, mas as visitas ainda não foram marcadas como concluídas.</Text>}
      {group.items.map(item => {
      const status = itineraryStatus(item, now);
      return <View key={item.id} style={[s.secondary, { borderRadius: 14, padding: 16, gap: 12 }]}>
        <Text style={[s.label, { color: status.color }]}>{status.label}</Text>
        <Text style={s.cardTitle}>{item.nome}</Text>
        <Text style={s.subtitle}>{item.cidade} · {item.data.split('-').reverse().join('/')} às {item.partida}</Text>
        {item.totalParadas !== undefined && <Text style={s.text}>{item.paradasConcluidas ?? 0} de {item.totalParadas} paradas concluídas</Text>}
        <Text style={s.small}>{item.pessoas} pessoa(s) · {price(item.totalCentavos / 100)} previsto</Text>
        <Text style={s.small}>{offlineLabel(item.id)}</Text>
        <Button title={copyDates?.[item.id] ? 'Atualizar cópia offline' : 'Baixar este roteiro'} secondary disabled={busy || downloading} onPress={() => { void download(item.id); }} />
        <Button title={status.action} onPress={() => router.push({ pathname: '/itineraries/[id]', params: { id: item.id } })} />
      </View>;
      })}
    </Fragment>)}
    {header && !error && <>
      {editingName ? <View style={{ gap: 12 }}>
        <Field label="Nome do rolê" value={name} onFocus={focusField} setValue={value => { if (!renaming.current) setName(value); }} />
        <Message text={nameError} />
        <Button title="Salvar nome" busy={busy} disabled={downloading} onPress={() => { void rename(); }} />
        <Button title="Cancelar edição" secondary disabled={busy} onPress={() => { setEditingName(false); setNameError(''); }} />
      </View> : <Button title="Editar nome" secondary disabled={busy || downloading} onPress={() => { setName(header.nome); setNameError(''); setEditingName(true); }} />}
      <Text style={s.subtitle}>{header.cidade} • {header.data.split('-').reverse().join('/')} • {header.pessoas} pessoa(s)</Text>
      <Text style={s.label}>{nextIndex === -1 ? 'Rolê concluído!' : `${completedCount} de ${detail?.paradas.length} paradas concluídas`}</Text>
      {timeline && nextIndex >= 0 && !busy && <>
        <Text style={s.text}>Próxima parada: {timeline[nextIndex].role.nome}</Text>
        <DirectionsButton role={timeline[nextIndex].role} title={timeline.length === 1 ? 'Ver como chegar lá' : nextIndex === 0 ? 'Ver rota da primeira parada' : 'Ver rota da próxima parada'} />
      </>}
      <Text style={s.text}>Total registrado: {price(header.totalCentavos / 100)}</Text><Text style={s.subtitle}>Por pessoa: {price(header.totalCentavos / header.pessoas / 100)} • Limite do grupo: {price(header.orcamentoCentavos / 100)}</Text>
      <Text style={s.small}>Valores registrados ao salvar. Confirme preços, funcionamento e disponibilidade antes de sair. Os horários são estimativas, sem deslocamento.</Text>
      {timeline?.map((stop, index) => <View key={stop.role.id} style={[s.secondary, { borderRadius: 14, padding: 16, gap: 10 }]}><Text style={s.cardTitle}>{index + 1}. {stop.role.nome}</Text>
        {detail?.paradas[index].cancelado && <Message text="Evento cancelado. O planejamento original foi preservado; revise esta parada." />}
        <Text style={s.small}>{stop.horario ? `Horário marcado: ${stop.horario}` : "Visita flexível"}</Text><Text style={s.label}>{detail?.paradas[index].concluida ? 'Concluída' : index === nextIndex ? 'Próxima parada' : 'Pendente'}</Text>
        <Text style={s.subtitle}>{stop.from} → {stop.to} • {stop.minutes} min</Text><Text style={s.text}>{stop.role.local ?? 'Local não informado'}</Text><Text style={s.small}>{price(stop.role.valor)} por pessoa no momento do salvamento</Text>
        <Button title={detail?.paradas[index].concluida ? 'Desfazer conclusão' : 'Marcar como concluída'} disabled={busy || downloading} onPress={() => { void progress(stop.role.id, !detail?.paradas[index].concluida); }} />
        <Button title={`Consultar ${stop.role.nome}`} secondary onPress={() => router.push({ pathname: '/roles/[id]', params: { id: stop.role.id } })} /></View>)}
      {token && !busy && !downloading && <DeleteItinerary key={`${token}:${header.id}`} id={header.id} name={header.nome} token={token} />}
    </>}
  </ScrollView></FormKeyboardView>;
}
