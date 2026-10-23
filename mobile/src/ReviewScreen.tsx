import { describeCharacteristics, type Characteristics } from './roleCharacteristics';
import { describeHours, type HoursRule } from './openingHours';
import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { Redirect, router, useFocusEffect } from 'expo-router';
import { useAuth } from './AuthContext';
import { ApiError, request } from './api';
import { Button, Field, Message, price } from './ui';
import { colors, styles as s } from './theme';

type Details = { nome: string; status: string } & Record<string, string | number | boolean | HoursRule[] | Characteristics | null>;
type Item = { organizacaoId: number; localId: number | null; organizacao: Details; local: Details | null; versao: string; publicacao?: Details | null; role?: Details & { id: number; versao: number; valor: number; data: string | null } };
const labels: Record<string, string> = { caracteristicas: 'Características declaradas', funcionamento: 'Funcionamento da atividade', nome: 'Nome', tipo: 'Tipo', descricao: 'Descrição', contatoEmail: 'E-mail de contato', contatoTelefone: 'Telefone', status: 'Status', categoria: 'Categoria', endereco: 'Endereço', cidade: 'Cidade', uf: 'UF', cep: 'CEP', acessibilidade: 'Acessibilidade', valor: 'Preço por pessoa', data: 'Data', horario: 'Início do evento', dataFim: 'Data de término (intervalo contínuo)', horarioFim: 'Horário de término', cancelado: 'Cancelado', motivoCancelamento: 'Motivo do cancelamento', latitude: 'Latitude do local', longitude: 'Longitude do local' };
export function ReviewScreen() {
  const { session } = useAuth();
  const [kind, setKind] = useState<'cadastros' | 'roles'>('cadastros');
  if (!session) return <Redirect href="/auth" />;
  if (!session.conta.administrador) return <Redirect href="/account" />;
  return <View style={{ flex: 1 }}><View style={{ padding: 12, gap: 8 }}><Button title="Organizações e locais" secondary={kind !== 'cadastros'} onPress={() => setKind('cadastros')} /><Button title="Rolês para análise" secondary={kind !== 'roles'} onPress={() => setKind('roles')} /></View><Queue key={`${session.token}:${kind}`} token={session.token} kind={kind} /></View>;
}
function Queue({ token, kind }: { token: string; kind: 'cadastros' | 'roles' }) {
  const { expire } = useAuth(); const active = useRef(false);
  const endpoint = kind === 'roles' ? '/revisoes-roles' : '/revisoes';
  const [items, setItems] = useState<Item[]>([]); const [selected, setSelected] = useState<Item | null>(null);
  const [reason, setReason] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [offset, setOffset] = useState(0); const [more, setMore] = useState(false); const [notice, setNotice] = useState('');
  const load = useCallback(async (page = 0) => {
    setBusy(true); setError('');
    try {
      const result = await request<{ itens: Item[] }>(`${endpoint}?deslocamento=${page}`, { token });
      if (active.current) { setItems(result.itens); setOffset(page); setMore(result.itens.length === 20); setSelected(null); setReason(''); }
    } catch (e) {
      if (!active.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else { setItems([]); setSelected(null); setError(e instanceof Error ? e.message : 'Não foi possível carregar.'); }
    } finally { if (active.current) setBusy(false); }
  }, [token, expire, endpoint]);
  useFocusEffect(useCallback(() => { active.current = true; void load(); return () => { active.current = false; }; }, [load]));
  async function decide(choice: 'APROVAR' | 'REJEITAR') {
    if (!selected) return;
    if (!reason.trim() || reason.trim().length > 2000) { setError('Escreva uma justificativa de até 2.000 caracteres.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      await request(endpoint, { token, method: 'POST', body: selected.role ? { rascunhoId: selected.role.id, versao: selected.role.versao, decisao: choice, justificativa: reason } : { organizacaoId: selected.organizacaoId, localId: selected.localId, versao: selected.versao, decisao: choice, justificativa: reason } });
      if (!active.current) return;
      setNotice('Decisão registrada. O responsável poderá consultar a justificativa.'); await load();
    } catch (e) {
      if (!active.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError(e instanceof ApiError ? e.message : 'Não foi possível confirmar a decisão. Atualize a fila antes de tentar novamente.');
    } finally { if (active.current) setBusy(false); }
  }
  return <ScrollView contentContainerStyle={[s.content, { maxWidth: 760, width: '100%', alignSelf: 'center' }]} keyboardShouldPersistTaps="handled">
    <Button title={selected ? '← Voltar à fila' : '← Minha conta'} secondary disabled={busy} onPress={() => { setError(''); if (selected) setSelected(null); else router.replace('/account'); }} />
    <Text style={s.title}>{kind === 'roles' ? 'Análise de rolês' : 'Análise de cadastros'}</Text><Message text={error} />{!!notice && <Text style={s.text}>{notice}</Text>}
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando análise" />}
    <Button title="Atualizar fila" secondary disabled={busy} onPress={() => { void load(); }} />
    {selected ? <>
      <Text style={s.cardTitle}>Organização</Text><Detail data={selected.organizacao} />
      {selected.local && <><Text style={s.cardTitle}>Local</Text><Detail data={selected.local} /></>}
      {selected.publicacao && <><Text style={s.cardTitle}>Publicação atual</Text><Detail data={selected.publicacao} /></>}
      {selected.role && <><Text style={s.cardTitle}>Proposta para análise</Text><Detail data={selected.role} /></>}
      <Text style={s.small}>Confira os dados antes de decidir. A justificativa será exibida ao responsável. Cadastros vinculados à sua conta não podem ser revisados por você.</Text>
      <Field label="Justificativa da decisão" value={reason} setValue={setReason} />
      {selected.local && selected.organizacao.status !== 'APROVADA' && <Text style={s.subtitle}>A organização precisa ser aprovada antes deste local.</Text>}
      <Button title={selected.role ? selected.role.cancelado ? 'Aprovar cancelamento' : selected.publicacao ? 'Aprovar alteração' : 'Aprovar e publicar rolê' : selected.local ? 'Aprovar e publicar local' : 'Aprovar organização'} disabled={busy || !!selected.local && selected.organizacao.status !== 'APROVADA' || !!selected.role && selected.local?.status !== 'PUBLICADO'} onPress={() => { void decide('APROVAR'); }} />
      {selected.role && selected.local?.status !== 'PUBLICADO' && <Text style={s.subtitle}>O local precisa estar aprovado antes da publicação do rolê.</Text>}
      <Button title="Rejeitar e solicitar correção" secondary disabled={busy} onPress={() => { void decide('REJEITAR'); }} />
    </> : <>
      {!busy && !error && items.length === 0 && <Text style={s.subtitle}>Nenhum cadastro pendente nesta página.</Text>}
      {items.map(item => <View key={item.role ? `role:${item.role.id}` : `${item.organizacaoId}:${item.localId ?? 'org'}`} style={[s.secondary, { padding: 16, borderRadius: 12, gap: 10 }]}><Text style={s.cardTitle}>{item.role?.nome ?? item.local?.nome ?? item.organizacao.nome}</Text><Text style={s.small}>{item.local ? `Local de ${item.organizacao.nome}` : 'Organização'}</Text><Button title={`Revisar ${item.role?.nome ?? item.local?.nome ?? item.organizacao.nome}`} secondary disabled={busy} onPress={() => { setSelected(item); setReason(''); setError(''); setNotice(''); }} /></View>)}
      {offset > 0 && <Button title="Página anterior" secondary disabled={busy} onPress={() => { void load(Math.max(0, offset - 20)); }} />}
      {more && <Button title="Próxima página" secondary disabled={busy} onPress={() => { void load(offset + 20); }} />}
    </>}
  </ScrollView>;
}
function Detail({ data }: { data: Details }) {
  return <View style={[s.secondary, { padding: 16, borderRadius: 12, gap: 8 }]}>{Object.entries(data).filter(([key]) => labels[key]).map(([key, value]) => <Text key={key} style={s.text}>{labels[key]}: {value === null || value === '' ? 'Não informado' : key === 'caracteristicas' ? describeCharacteristics(value as Characteristics) : key === 'funcionamento' ? describeHours(value as HoursRule[]) : typeof value === 'boolean' ? value ? 'Sim' : 'Não' : key === 'valor' ? price(Number(value)) : (key === 'data' || key === 'dataFim') ? String(value).split('-').reverse().join('/') : String(value)}</Text>)}</View>;
}
