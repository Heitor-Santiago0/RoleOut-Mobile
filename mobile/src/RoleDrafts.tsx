import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { formatDateInput, isoDate, moneyCents } from './itinerary';
import { Button, Field, Message, price } from './ui';
import { colors, styles as s } from './theme';

type Draft = { id: number; localId: number; nome: string; descricao: string | null; categoria: string; data: string | null; valor: number; versao: number; status: string; roleId: number | null };
type Review = { id: number; versao: number; decisao: string; justificativa: string; criadoEm: string };
const statuses: Record<string, string> = { RASCUNHO: 'Rascunho', PENDENTE: 'Aguardando análise', REJEITADO: 'Correção solicitada', PUBLICADO: 'Publicado' };
const blank = { nome: '', descricao: '', categoria: '', data: '', valor: '' };
export function RoleDrafts({ token, venue, canEdit, onBack }: { token: string; venue: { id: number; nome: string }; canEdit: boolean; onBack: () => void }) {
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
    setForm(draft ? { nome: draft.nome, descricao: draft.descricao ?? '', categoria: draft.categoria, data: draft.data?.split('-').reverse().join('/') ?? '', valor: draft.valor.toFixed(2).replace('.', ',') } : { ...blank });
  }
  async function save() {
    if (!form || pending.current || uncertain) return;
    const cents = moneyCents(form.valor); const date = form.data ? isoDate(form.data) : null;
    if (!form.nome.trim() || form.nome.trim().length > 200 || !form.categoria.trim() || form.categoria.trim().length > 100 || form.descricao.trim().length > 4000 || cents === null || (form.data && !date)) {
      setError('Confira nome (até 200 caracteres), categoria (até 100), descrição (até 4000), preço e data. Use zero para gratuito.'); return;
    }
    let savedSuccessfully = false;
    pending.current = true; setBusy(true); setError(''); setNotice('');
    try {
      const saved = await request<Draft>(`/rascunhos-roles/${venue.id}${editing ? `/${editing.id}` : ''}`, {
        token, method: editing ? 'PUT' : 'POST', body: { nome: form.nome, descricao: form.descricao, categoria: form.categoria, data: date, valor: cents / 100, ...(editing ? { versao: editing.versao } : {}) },
      });
      if (active.current) {
        setEditing(saved); setForm(null); savedSuccessfully = true;
        setNotice('Rascunho salvo. Ele ainda não aparece para o público.');
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
      <Field label="Nome do rolê" value={form.nome} setValue={nome => setForm(v => v && ({ ...v, nome }))} />
      <Field label="Categoria (ex.: Cultura, Música, Gastronomia)" value={form.categoria} setValue={categoria => setForm(v => v && ({ ...v, categoria }))} />
      <Field label="Descrição (opcional)" value={form.descricao} setValue={descricao => setForm(v => v && ({ ...v, descricao }))} />
      <Field label="Preço estimado por pessoa (R$)" keyboardType="decimal-pad" placeholder="0,00" value={form.valor} setValue={valor => setForm(v => v && ({ ...v, valor }))} />
      <Field label="Data (opcional)" keyboardType="number-pad" placeholder="DD/MM/AAAA" value={form.data} setValue={data => setForm(v => v && ({ ...v, data: formatDateInput(data) }))} />
      <Text style={s.small}>Use zero para gratuito. Deixe a data vazia para atividades sem data específica.</Text>
      <Button title="Salvar rascunho do rolê" busy={busy} disabled={!canEdit || uncertain} onPress={() => { void save(); }} />
      <Button title={uncertain ? 'Atualizar lista' : 'Cancelar edição'} secondary disabled={busy} onPress={() => { if (uncertain) void load(offset); else { setForm(null); setError(''); } }} />
    </> : <>
      {canEdit && <Button title="Criar rascunho de rolê" disabled={busy} onPress={() => open()} />}
      <Button title="Atualizar rascunhos" secondary disabled={busy} onPress={() => { void load(offset); }} />
      {!busy && !error && items.length === 0 && <Text style={s.subtitle}>Nenhum rascunho nesta página.</Text>}
      {items.map(item => <View key={item.id} style={[s.secondary, { padding: 16, borderRadius: 14, gap: 8 }]}>
        <Text style={s.cardTitle}>{item.nome}</Text><Text style={s.subtitle}>{statuses[item.status] ?? item.status} • {item.categoria}</Text>
        <Text style={s.text}>{price(item.valor)}{item.valor > 0 ? ' por pessoa' : ''} • {item.data ? item.data.split('-').reverse().join('/') : 'Sem data específica'}</Text>
        {!!item.descricao && <Text style={s.text}>{item.descricao}</Text>}
        {canEdit && ['RASCUNHO', 'REJEITADO'].includes(item.status) && <><Button title={`Editar ${item.nome}`} secondary disabled={busy} onPress={() => open(item)} /><Button title={`Enviar ${item.nome} para análise`} disabled={busy} onPress={() => { void submit(item); }} /></>}
        <Button title={`Histórico de ${item.nome}`} secondary disabled={busy} onPress={() => { void showHistory(item); }} />
      </View>)}
      {history && <View style={{ gap: 8 }}><Text style={s.cardTitle}>Histórico: {history.name}</Text>{history.items.length === 0 && <Text style={s.small}>Nenhuma decisão registrada.</Text>}{history.items.map(review => <View key={review.id} style={[s.secondary, { padding: 14, borderRadius: 12, gap: 6 }]}><Text style={s.text}>{review.decisao === 'APROVAR' ? 'Aprovado' : 'Correção solicitada'}</Text><Text style={s.text}>{review.justificativa}</Text><Text style={s.small}>{new Date(review.criadoEm).toLocaleString('pt-BR')}</Text></View>)}{history.items.length === 100 && <Text style={s.small}>Exibindo as 100 decisões mais recentes.</Text>}</View>}
      {offset > 0 && <Button title="Página anterior" secondary disabled={busy} onPress={() => { void load(Math.max(0, offset - 20)); }} />}
      {more && <Button title="Próxima página" secondary disabled={busy} onPress={() => { void load(offset + 20); }} />}
    </>}
  </View>;
}
