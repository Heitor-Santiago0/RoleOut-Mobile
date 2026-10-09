import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { Redirect, router, useFocusEffect } from 'expo-router';
import { useAuth } from './AuthContext';
import { ApiError, request } from './api';
import { Button, Field, Message } from './ui';
import { colors, styles as s } from './theme';

type Details = { nome: string; status: string } & Record<string, string | number | null>;
type Item = { organizacaoId: number; localId: number | null; organizacao: Details; local: Details | null; versao: string };
const labels: Record<string, string> = { nome: 'Nome', tipo: 'Tipo', descricao: 'Descrição', contatoEmail: 'E-mail de contato', contatoTelefone: 'Telefone', status: 'Status', categoria: 'Categoria', endereco: 'Endereço', cidade: 'Cidade', uf: 'UF', cep: 'CEP', acessibilidade: 'Acessibilidade' };
export function ReviewScreen() {
  const { session } = useAuth();
  if (!session) return <Redirect href="/auth" />;
  if (!session.conta.administrador) return <Redirect href="/account" />;
  return <Queue key={session.token} token={session.token} />;
}
function Queue({ token }: { token: string }) {
  const { expire } = useAuth(); const active = useRef(false);
  const [items, setItems] = useState<Item[]>([]); const [selected, setSelected] = useState<Item | null>(null);
  const [reason, setReason] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [offset, setOffset] = useState(0); const [more, setMore] = useState(false); const [notice, setNotice] = useState('');
  const load = useCallback(async (page = 0) => {
    setBusy(true); setError('');
    try {
      const result = await request<{ itens: Item[] }>(`/revisoes?deslocamento=${page}`, { token });
      if (active.current) { setItems(result.itens); setOffset(page); setMore(result.itens.length === 20); setSelected(null); setReason(''); }
    } catch (e) {
      if (!active.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else { setItems([]); setSelected(null); setError(e instanceof Error ? e.message : 'Não foi possível carregar.'); }
    } finally { if (active.current) setBusy(false); }
  }, [token, expire]);
  useFocusEffect(useCallback(() => { active.current = true; void load(); return () => { active.current = false; }; }, [load]));
  async function decide(choice: 'APROVAR' | 'REJEITAR') {
    if (!selected) return;
    if (!reason.trim() || reason.trim().length > 2000) { setError('Escreva uma justificativa de até 2.000 caracteres.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      await request('/revisoes', { token, method: 'POST', body: { organizacaoId: selected.organizacaoId, localId: selected.localId, versao: selected.versao, decisao: choice, justificativa: reason } });
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
    <Text style={s.title}>Análise de cadastros</Text><Message text={error} />{!!notice && <Text style={s.text}>{notice}</Text>}
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando análise" />}
    <Button title="Atualizar fila" secondary disabled={busy} onPress={() => { void load(); }} />
    {selected ? <>
      <Text style={s.cardTitle}>Organização</Text><Detail data={selected.organizacao} />
      {selected.local && <><Text style={s.cardTitle}>Local</Text><Detail data={selected.local} /></>}
      <Text style={s.small}>Confira os dados antes de decidir. A justificativa será exibida ao responsável. Cadastros vinculados à sua conta não podem ser revisados por você.</Text>
      <Field label="Justificativa da decisão" value={reason} setValue={setReason} />
      {selected.local && selected.organizacao.status !== 'APROVADA' && <Text style={s.subtitle}>A organização precisa ser aprovada antes deste local.</Text>}
      <Button title={selected.local ? 'Aprovar e publicar local' : 'Aprovar organização'} disabled={busy || !!selected.local && selected.organizacao.status !== 'APROVADA'} onPress={() => { void decide('APROVAR'); }} />
      <Button title="Rejeitar e solicitar correção" secondary disabled={busy} onPress={() => { void decide('REJEITAR'); }} />
    </> : <>
      {!busy && !error && items.length === 0 && <Text style={s.subtitle}>Nenhum cadastro pendente nesta página.</Text>}
      {items.map(item => <View key={`${item.organizacaoId}:${item.localId ?? 'org'}`} style={[s.secondary, { padding: 16, borderRadius: 12, gap: 10 }]}><Text style={s.cardTitle}>{item.local?.nome ?? item.organizacao.nome}</Text><Text style={s.small}>{item.local ? `Local de ${item.organizacao.nome}` : 'Organização'}</Text><Button title={`Revisar ${item.local?.nome ?? item.organizacao.nome}`} secondary disabled={busy} onPress={() => { setSelected(item); setReason(''); setError(''); setNotice(''); }} /></View>)}
      {offset > 0 && <Button title="Página anterior" secondary disabled={busy} onPress={() => { void load(Math.max(0, offset - 20)); }} />}
      {more && <Button title="Próxima página" secondary disabled={busy} onPress={() => { void load(offset + 20); }} />}
    </>}
  </ScrollView>;
}
function Detail({ data }: { data: Details }) {
  return <View style={[s.secondary, { padding: 16, borderRadius: 12, gap: 8 }]}>{Object.entries(data).filter(([key]) => labels[key]).map(([key, value]) => <Text key={key} style={s.text}>{labels[key]}: {value === null || value === '' ? 'Não informado' : String(value)}</Text>)}</View>;
}
