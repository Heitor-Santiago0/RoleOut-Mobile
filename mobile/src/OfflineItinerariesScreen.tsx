import { Fragment, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, Pressable, ScrollView, Text, View } from 'react-native';
import { offlineItineraries } from './offlineItineraries';
import type { OfflineEntry } from './offlineStore';
import { readToken } from './session';
import { Button, Field, price } from './ui';
import { colors, styles as s } from './theme';
import { schedule } from './itinerary';
import { groupItineraries, searchItineraries, type ItineraryFilter } from './itineraryGroups';
import { FormKeyboardView } from './FormKeyboardView';
import { useFormKeyboard } from './useFormKeyboard';

export function OfflineItinerariesScreen({ onBack }: { onBack: () => void }) {
  const [entries, setEntries] = useState<OfflineEntry[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<ItineraryFilter>('all');
  const [now, setNow] = useState(() => new Date());
  const scroll = useRef<ScrollView>(null);
  const focusField = useFormKeyboard(scroll);
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30000);
    return () => clearInterval(timer);
  }, []);
  function select(id: number | null) {
    Keyboard.dismiss();
    setSelected(id);
    scroll.current?.scrollTo({ y: 0, animated: false });
  }
  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const token = await readToken();
        const data = token ? await offlineItineraries.read(token) : [];
        if (active) setEntries(data.sort((a, b) => a.data.roteiro.data.localeCompare(b.data.roteiro.data)));
      } catch { if (active) setError('Não foi possível ler os roteiros deste aparelho.'); }
      finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, []);
  const entry = entries.find(item => item.data.roteiro.id === selected);
  const summaries = entries.map(item => ({ ...item.data.roteiro,
    totalParadas: item.data.paradas.length,
    paradasConcluidas: item.data.paradas.filter(stop => stop.concluida).length,
  }));
  const groups = groupItineraries(searchItineraries(summaries, query), now, filter);
  const timeline = entry && schedule(entry.data.paradas.map(stop => ({ role: {
    id: stop.roleId, nome: stop.nome, local: stop.local, cidade: entry.data.roteiro.cidade,
    data: entry.data.roteiro.data, valor: stop.precoCentavos / 100, descricao: null, categoria: null,
  }, minutes: stop.minutos, horario: stop.horario ?? undefined })), entry.data.roteiro.partida);
  return <FormKeyboardView><ScrollView ref={scroll} style={s.root} contentContainerStyle={[s.content, { paddingBottom: 120 }]} keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag">
    <Button title="← Voltar" secondary onPress={() => selected === null ? onBack() : select(null)} />
    <Text accessibilityRole="header" style={s.title}>{entry?.data.roteiro.nome ?? 'Roteiros offline'}</Text>
    <Text style={s.subtitle}>Cópias salvas neste aparelho. Você pode consultá-las sem internet. Alterações, disponibilidade dos locais e mapas precisam de conexão.</Text>
    {loading && <ActivityIndicator color={colors.blue} />}
    {!!error && <Text accessibilityRole="alert" style={s.error}>{error}</Text>}
    {!loading && !error && entries.length === 0 && <Text style={s.text}>Nenhum roteiro disponível offline. Quando estiver conectado, abra Meus rolês e toque em Baixar roteiros para usar offline.</Text>}
    {!entry && entries.length > 0 && <>
      <Field label="Buscar nos roteiros offline" placeholder="Nome do rolê ou cidade" value={query} setValue={setQuery} onFocus={focusField} />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {([{ key: 'all', title: 'Todos' }, { key: 'active', title: 'A fazer' }, { key: 'completed', title: 'Concluídos' }] as const).map(option =>
          <Pressable key={option.key} accessibilityRole="button" accessibilityState={{ selected: filter === option.key }} style={[s.chip, filter === option.key && s.selected]} onPress={() => setFilter(option.key)}>
            <Text style={[s.chipText, filter === option.key && s.selectedText]}>{option.title}</Text>
          </Pressable>)}
      </View>
      <Text style={s.small}>{groups.reduce((count, group) => count + group.items.length, 0)} de {entries.length} roteiro(s) neste aparelho. Progresso da última cópia salva.</Text>
      {groups.length === 0 && <Text style={s.text}>Nenhum roteiro corresponde à busca e ao filtro selecionado.</Text>}
      {(query.trim() || filter !== 'all') && <Button title="Limpar busca e filtros" secondary onPress={() => { setQuery(''); setFilter('all'); }} />}
      {groups.map(group => <Fragment key={group.key}>
        <Text accessibilityRole="header" style={s.cardTitle}>{group.title} · {group.items.length}</Text>
        {group.items.map(item => <View key={item.id} style={[s.secondary, { padding: 16, borderRadius: 14, gap: 12 }]}>
          <Text style={s.cardTitle}>{item.nome}</Text>
          <Text style={s.text}>{item.cidade} · {item.data.split('-').reverse().join('/')} às {item.partida}</Text>
          <Text style={s.small}>{item.totalParadas} parada(s) · {price(item.totalCentavos / 100)}</Text>
          <Text style={s.small}>Última cópia: {new Date(entries.find(copy => copy.data.roteiro.id === item.id)!.savedAt).toLocaleString('pt-BR')}</Text>
          <Button title="Consultar roteiro offline" onPress={() => select(item.id)} />
        </View>)}
      </Fragment>)}
    </>}
    {entry && <>
      <Text style={s.text}>{entry.data.roteiro.cidade} · {entry.data.roteiro.data.split('-').reverse().join('/')} às {entry.data.roteiro.partida}</Text>
      <Text style={s.small}>Última cópia: {new Date(entry.savedAt).toLocaleString('pt-BR')}. As informações podem ter mudado desde então.</Text>
      <Text style={s.text}>{entry.data.roteiro.pessoas} pessoa(s) · Total registrado: {price(entry.data.roteiro.totalCentavos / 100)}</Text>
      <Text style={s.small}>Horários estimados, sem tempo de deslocamento.</Text>
      {timeline?.map((stop, index) => <View key={`${index}:${stop.role.id}`} style={[s.secondary, { padding: 16, borderRadius: 14, gap: 8 }]}>
        <Text style={s.cardTitle}>{index + 1}. {stop.role.nome}</Text>
        <Text style={s.text}>{stop.role.local ?? 'Local não informado'}</Text>
        <Text style={s.subtitle}>{stop.from} → {stop.to} · {stop.minutes} min</Text>
        {entry.data.paradas[index].cancelado && <Text style={s.text}>Evento cancelado na última atualização.</Text>}
        {stop.horario && <Text style={s.text}>Horário marcado: {stop.horario}</Text>}
        <Text style={s.small}>{entry.data.paradas[index].concluida ? 'Concluída na última atualização' : 'Pendente na última atualização'} · {price(stop.role.valor)} por pessoa</Text>
      </View>)}
    </>}
  </ScrollView></FormKeyboardView>;
}
