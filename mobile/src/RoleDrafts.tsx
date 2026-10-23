import { CharacteristicsEditor } from './CharacteristicsEditor';
import type { Characteristics } from './roleCharacteristics';
import { OpeningHoursEditor } from './OpeningHoursEditor';
import { hoursRows, parseHours, type HoursRow, type HoursRule } from './openingHours';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View, type TextInputProps } from 'react-native';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { formatDateInput, formatTimeInput, isoDate, moneyCents } from './itinerary';
import { Button, Field, Message, price } from './ui';
import { colors, styles as s } from './theme';

type Draft = { caracteristicas?: Characteristics | null; funcionamento?: HoursRule[] | null; id: number; localId: number; nome: string; descricao: string | null; categoria: string; data: string | null; valor: number; versao: number; status: string; roleId: number | null; horario?: string | null; horarioFim?: string | null; dataFim?: string | null; cancelado?: boolean; motivoCancelamento?: string | null };
type Review = { id: number; versao: number; decisao: string; justificativa: string; criadoEm: string; conteudo?: string | null };
const statuses: Record<string, string> = { RASCUNHO: 'Rascunho', PENDENTE: 'Aguardando análise', REJEITADO: 'Correção solicitada', PUBLICADO: 'Publicado' };
const blank = { caracteristicas: { musica: [], comida: [], ambientes: [] } as Characteristics, hours: [] as HoursRow[], nome: '', descricao: '', categoria: '', data: '', horario: '', horarioFim: '', dataFim: '', valor: '', cancelado: false, motivoCancelamento: '' };
export function RoleDrafts({ token, venue, canEdit, onBack, onFieldFocus }: { onFieldFocus?: TextInputProps["onFocus"]; token: string; venue: { id: number; nome: string }; canEdit: boolean; onBack: () => void }) {
  const { expire } = useAuth(); const active = useRef(true); const pending = useRef(false);
  const [items, setItems] = useState<Draft[]>([]); const [offset, setOffset] = useState(0);
  const [more, setMore] = useState(false); const [busy, setBusy] = useState(true);
  const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const [form, setForm] = useState<typeof blank | null>(null); const [editing, setEditing] = useState<Draft | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [history, setHistory] = useState<{ name: string; items: Review[] } | null>(null);
  useEffect(() => {
    active.current = true;
    request<{ itens: Draft[] }>(`/rascunhos-roles/${venue.id}`, { token }).then(data => {
      if (active.current) { setItems(data.itens); setMore(data.itens.length === 20); }
    }).catch(e => {
      if (!active.current) return;
      if (e instanceof ApiError && e.status === 401) void expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível carregar.');
    }).finally(() => { if (active.current) setBusy(false); });
    return () => { active.current = false; };
  }, [token, venue.id, expire]);
  async function fail(e: unknown) {
    if (!active.current) return;
    if (e instanceof ApiError && e.status === 401) await expire();
    else setError(e instanceof Error ? e.message : 'Não foi possível concluir.');
  }
  async function load(next: number) {
    if (pending.current) return; pending.current = true; setBusy(true); setError('');
    try {
      const data = await request<{ itens: Draft[] }>(`/rascunhos-roles/${venue.id}?deslocamento=${next}`, { token });
      if (active.current) { setItems(data.itens); setOffset(next); setMore(data.itens.length === 20); setForm(null); setUncertain(false); }
    } catch (e) { await fail(e); } finally { pending.current = false; if (active.current) setBusy(false); }
  }
  function open(draft?: Draft) {
    setEditing(draft ?? null); setError(''); setNotice(''); setUncertain(false);
    setForm(draft ? { caracteristicas: draft.caracteristicas ?? { musica: [], comida: [], ambientes: [] }, hours: hoursRows(draft.funcionamento), nome: draft.nome, horario: draft.horario ?? '', horarioFim: draft.horarioFim ?? '', dataFim: draft.dataFim?.split('-').reverse().join('/') ?? '', cancelado: draft.cancelado ?? false, motivoCancelamento: draft.motivoCancelamento ?? '', descricao: draft.descricao ?? '', categoria: draft.categoria, data: draft.data?.split('-').reverse().join('/') ?? '', valor: draft.valor.toFixed(2).replace('.', ',') } : { ...blank });
  }
  async function save() {
    if (!form || pending.current || uncertain) return;
    let funcionamento: HoursRule[];
    try { funcionamento = parseHours(form.hours); } catch (e) { setError(e instanceof Error ? e.message : 'Confira os horários.'); return; }
    const cents = moneyCents(form.valor); const date = form.data ? isoDate(form.data) : null;
    const endDate = form.dataFim ? isoDate(form.dataFim) : null;
    if (form.dataFim && (!endDate || !date || !form.horario || !form.horarioFim || endDate < date)) { setError("Informe início e término completos, com datas em ordem."); return; }
    if ((form.horario && (!date || !/^([01]\d|2[0-3]):[0-5]\d$/.test(form.horario))) || !form.nome.trim() || form.nome.trim().length > 200 || !form.categoria.trim() || form.categoria.trim().length > 100 || form.descricao.trim().length > 4000 || cents === null || (form.data && !date)) {
      setError('Confira nome (até 200 caracteres), categoria (até 100), descrição (até 4000), preço e data. Use zero para gratuito.'); return;
    }
    if (form.horarioFim && (!form.horario || !/^([01]\d|2[0-3]):[0-5]\d$/.test(form.horarioFim) || ((!endDate || endDate === date) && form.horarioFim <= form.horario))) {
      setError('O término deve ser posterior ao início. Se passar da meia-noite, preencha a data de término.'); return;
    }
    if (form.cancelado && (!form.motivoCancelamento.trim() || form.motivoCancelamento.trim().length > 2000)) {
      setError('Informe o motivo do cancelamento, com até 2.000 caracteres. Ele ficará público após aprovação.'); return;
    }
    let savedSuccessfully = false;
    pending.current = true; setBusy(true); setError(''); setNotice('');
    try {
      const saved = await request<Draft>(`/rascunhos-roles/${venue.id}${editing ? `/${editing.id}` : ''}`, {
        token, method: editing ? 'PUT' : 'POST', body: { caracteristicas: form.caracteristicas, funcionamento, nome: form.nome, descricao: form.descricao, categoria: form.categoria, data: date, horario: form.horario || null, horarioFim: form.horarioFim || null, dataFim: endDate, cancelado: form.cancelado, motivoCancelamento: form.cancelado ? form.motivoCancelamento : null, valor: cents / 100, ...(editing ? { versao: editing.versao } : {}) },
      });
      if (active.current) {
        setEditing(saved); setForm(null); savedSuccessfully = true;
        setNotice(saved.roleId ? 'Proposta salva. A publicação atual permanece igual até a aprovação. Envie a proposta para análise.' : 'Rascunho salvo. Ele ainda não aparece para o público.');
      }
    } catch (e) {
      await fail(e);
      if (active.current && (!(e instanceof ApiError) || e.status === 409 || e.status >= 500)) {
        setUncertain(true); setError('Atualize a lista antes de tentar novamente: a gravação pode ter ocorrido ou o cadastro pode ter mudado. Copie suas alterações se precisar preservá-las.');
      }
    } finally { pending.current = false; if (active.current) setBusy(false); }
    if (savedSuccessfully && active.current) await load(editing ? offset : 0);
  }
  async function submit(item: Draft) {
    if (pending.current) return; pending.current = true; setBusy(true); setError(''); setNotice(''); setHistory(null);
    try {
      const saved = await request<Draft>(`/rascunhos-roles/${venue.id}/${item.id}/enviar`, { token, method: 'POST', body: { versao: item.versao } });
      if (active.current) { setItems(previous => previous.map(value => value.id === saved.id ? saved : value)); setNotice('Rolê enviado para análise. Aguarde a decisão antes de editar.'); }
    } catch (e) { await fail(e); }
    finally { pending.current = false; if (active.current) setBusy(false); }
  }
  async function showHistory(item: Draft) {
    if (pending.current) return; pending.current = true; setBusy(true); setError(''); setHistory(null);
    try {
      const data = await request<{ itens: Review[] }>(`/rascunhos-roles/${venue.id}/${item.id}/revisoes`, { token });
      if (active.current) setHistory({ name: item.nome, items: data.itens });
    } catch (e) { await fail(e); }
    finally { pending.current = false; if (active.current) setBusy(false); }
  }
  return <View style={{ gap: 14 }}>
    <Button title="← Voltar aos locais" secondary disabled={busy} onPress={onBack} />
    <Text style={s.cardTitle}>Rolês de {venue.nome}</Text>
    <Text style={s.subtitle}>Prepare os passeios, eventos e experiências oferecidos neste local. Confira o rascunho e envie para análise. O rolê só aparece no Explorar após aprovação.</Text>
    <Message text={error} />{!!notice && <Text accessibilityRole="alert" style={s.text}>{notice}</Text>}
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando rolês do local" />}
    {!canEdit && <Text style={s.small}>Para criar ou editar rolês, a organização e o local precisam estar aprovados.</Text>}
    {form ? <>
      {!!editing?.roleId && <View style={{ gap: 10 }}>
        <Text style={s.label}>Situação proposta: {form.cancelado ? 'Cancelar evento' : 'Evento ativo'}</Text>
        <Text style={s.small}>Alterações e cancelamento só entram em vigor após análise. A publicação atual permanece disponível até a decisão.</Text>
        <Button title={form.cancelado ? 'Propor evento ativo' : 'Propor cancelamento'} secondary disabled={busy} onPress={() => setForm(v => v && ({ ...v, cancelado: !v.cancelado }))} />
        {form.cancelado && <Field onFocus={onFieldFocus} label="Motivo do cancelamento (será público após aprovação)" value={form.motivoCancelamento} setValue={motivoCancelamento => setForm(v => v && ({ ...v, motivoCancelamento }))} />}
      </View>}
      <Field onFocus={onFieldFocus} label="Nome do rolê" value={form.nome} setValue={nome => setForm(v => v && ({ ...v, nome }))} />
      <Field onFocus={onFieldFocus} label="Categoria (ex.: Cultura, Música, Gastronomia)" value={form.categoria} setValue={categoria => setForm(v => v && ({ ...v, categoria }))} />
      <Field onFocus={onFieldFocus} label="Descrição (opcional)" value={form.descricao} setValue={descricao => setForm(v => v && ({ ...v, descricao }))} />
      <Field onFocus={onFieldFocus} label="Preço estimado por pessoa (R$)" keyboardType="decimal-pad" placeholder="0,00" value={form.valor} setValue={valor => setForm(v => v && ({ ...v, valor }))} />
      <Field onFocus={onFieldFocus} label="Data (opcional)" keyboardType="number-pad" placeholder="DD/MM/AAAA" value={form.data} setValue={data => setForm(v => v && ({ ...v, data: formatDateInput(data) }))} />
      <Field onFocus={onFieldFocus} label="Início do evento (opcional)" keyboardType="number-pad" placeholder="HH:MM" value={form.horario} setValue={horario => setForm(v => v && ({ ...v, horario: formatTimeInput(horario) }))} />
      <Field onFocus={onFieldFocus} label="Data de término (opcional; vazia usa o mesmo dia)" keyboardType="number-pad" placeholder="DD/MM/AAAA" value={form.dataFim} setValue={dataFim => setForm(v => v && ({ ...v, dataFim: formatDateInput(dataFim) }))} />
      <Field onFocus={onFieldFocus} label="Horário de término (opcional)" keyboardType="number-pad" placeholder="HH:MM" value={form.horarioFim} setValue={horarioFim => setForm(v => v && ({ ...v, horarioFim: formatTimeInput(horarioFim) }))} />
      <Text style={s.small}>Para eventos que atravessam a meia-noite, informe a data de término. O intervalo deve ser contínuo; não use para sessões separadas ou horários diários de funcionamento.</Text>
      <Text style={s.small}>Para um início marcado, informe também a data. Use zero para gratuito. Deixe a data vazia para atividades sem data específica.</Text>
      <CharacteristicsEditor token={token} value={form.caracteristicas} disabled={busy || uncertain} onChange={caracteristicas => setForm(v => v && ({ ...v, caracteristicas }))} />
      <OpeningHoursEditor onFieldFocus={onFieldFocus} rows={form.hours} disabled={busy || uncertain} onChange={hours => setForm(v => v && ({ ...v, hours }))} />
      <Button title="Salvar rascunho do rolê" busy={busy} disabled={!canEdit || uncertain} onPress={() => { void save(); }} />
      <Button title={uncertain ? 'Atualizar lista' : 'Cancelar edição'} secondary disabled={busy} onPress={() => { if (uncertain) void load(offset); else { setForm(null); setError(''); } }} />
    </> : <>
      {canEdit && <Button title="Criar rascunho de rolê" disabled={busy} onPress={() => open()} />}
      <Button title="Atualizar rascunhos" secondary disabled={busy} onPress={() => { void load(offset); }} />
      {!busy && !error && items.length === 0 && <Text style={s.subtitle}>Nenhum rascunho nesta página.</Text>}
      {items.map(item => <View key={item.id} style={[s.secondary, { padding: 16, borderRadius: 14, gap: 8 }]}>
        <Text style={s.cardTitle}>{item.nome}</Text><Text style={s.subtitle}>{statuses[item.status] ?? item.status} • {item.categoria}</Text>
        <Text style={s.text}>{price(item.valor)}{item.valor > 0 ? ' por pessoa' : ''} • {item.data ? item.data.split('-').reverse().join('/') : 'Sem data específica'}</Text>
        <Text style={s.small}>Início: {item.horario ?? 'não informado'} • Término: {item.horarioFim ?? 'não informado'}{item.dataFim ? ` em ${item.dataFim.split('-').reverse().join('/')}` : ''}</Text>
        {!!item.descricao && <Text style={s.text}>{item.descricao}</Text>}
        {!!item.roleId && <Text style={s.small}>{item.status === 'PUBLICADO' ? (item.cancelado ? 'Cancelamento aprovado' : 'Publicação ativa') : `Proposta de ${item.cancelado ? 'cancelamento' : 'alteração'}; a versão aprovada permanece em vigor.`}</Text>}
        {canEdit && item.status === 'PUBLICADO' && <Button title={`Propor alteração ou cancelamento de ${item.nome}`} secondary disabled={busy} onPress={() => open(item)} />}
        {canEdit && ['RASCUNHO', 'REJEITADO'].includes(item.status) && <><Button title={`Editar ${item.nome}`} secondary disabled={busy} onPress={() => open(item)} /><Button title={`Enviar ${item.nome} para análise`} disabled={busy} onPress={() => { void submit(item); }} /></>}
        <Button title={`Histórico de ${item.nome}`} secondary disabled={busy} onPress={() => { void showHistory(item); }} />
      </View>)}
      {history && <View style={{ gap: 8 }}><Text style={s.cardTitle}>Histórico: {history.name}</Text>{history.items.length === 0 && <Text style={s.small}>Nenhuma decisão registrada.</Text>}{history.items.map(review => <View key={review.id} style={[s.secondary, { padding: 14, borderRadius: 12, gap: 6 }]}><Text style={s.text}>{review.decisao === 'APROVAR' ? 'Aprovado' : 'Correção solicitada'}</Text><Text style={s.text}>{review.justificativa}</Text><Text style={s.small}>{review.conteudo ?? 'Decisão antiga: conteúdo da versão não disponível.'}</Text><Text style={s.small}>{new Date(review.criadoEm).toLocaleString('pt-BR')}</Text></View>)}{history.items.length === 100 && <Text style={s.small}>Exibindo as 100 decisões mais recentes.</Text>}</View>}
      {offset > 0 && <Button title="Página anterior" secondary disabled={busy} onPress={() => { void load(Math.max(0, offset - 20)); }} />}
      {more && <Button title="Próxima página" secondary disabled={busy} onPress={() => { void load(offset + 20); }} />}
    </>}
  </View>;
}
