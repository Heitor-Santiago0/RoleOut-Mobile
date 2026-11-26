import { useRef, useState } from 'react';
import { ScrollView, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { request, ApiError } from './api';
import { useAuth } from './AuthContext';
import { useSocialResource } from './useSocialResource';
import { Button, Field, Message } from './ui';
import { styles as s } from './theme';
import { FormKeyboardView } from './FormKeyboardView';
import { useFormKeyboard } from './useFormKeyboard';

type Author = { id: string; nome: string };
type Entry = { id: number; autor: Author; texto: string; nota: number | null; meu: boolean };
type Entries = { itens: Entry[]; proximo: number | null; total: number; media: number | null };
type Feed = { itens: { id: string; titulo: string; cidade: string; paradas: number; autor: Author }[]; proximo: number | null };

// Serialize taps and invalidate responses when the owning resource loses focus.
function useAction(token: string, generation: React.RefObject<number>) {
  const [busy, setBusy] = useState(false), [message, setMessage] = useState('');
  const pending = useRef(false); const { expire } = useAuth();
  async function run(path: string, method: string, body?: unknown, done?: () => void) {
    if (pending.current) return;
    pending.current = true; setBusy(true); setMessage(''); const version = generation.current;
    try { await request(path, { token, method, body }); if (generation.current === version) { setMessage('Salvo.'); done?.(); } }
    catch (e) { if (generation.current === version) { if (e instanceof ApiError && e.status === 401) void expire(); else setMessage(e instanceof Error ? e.message : 'Tente novamente.'); } }
    finally { pending.current = false; setBusy(false); }
  }
  return { busy, message, run };
}
export function FollowButton({ token, id }: { token: string; id: string }) {
  const path = `/comunidade/seguir/${id}`;
  const remote = useSocialResource<{ seguindo: boolean }>(path, token);
  const action = useAction(token, remote.generation);
  return <View style={{ gap: 8 }}><Message text={remote.error || action.message} />
    <Button title={remote.data?.seguindo ? 'Deixar de acompanhar' : 'Acompanhar passeios'} secondary disabled={!remote.data || remote.loading} busy={action.busy} onPress={() => { void action.run(path, remote.data?.seguindo ? 'DELETE' : 'PUT', undefined, remote.reload); }} />
  </View>;
}
export function CommunityFeed({ token }: { token: string }) {
  const [following, setFollowing] = useState(false), [pages, setPages] = useState<number[]>([0]);
  const after = pages[pages.length - 1];
  const remote = useSocialResource<Feed>(`/comunidade/feed?seguindo=${following}${after ? `&apos=${after}` : ''}`, token);
  return <ScrollView contentContainerStyle={s.content}>
    <Text style={s.title}>Passeios da comunidade</Text><Text style={s.subtitle}>Inspire-se e monte sua própria versão.</Text>
    <View style={{ flexDirection: 'row', gap: 8 }}><Button title="Todos" secondary={following} onPress={() => { setFollowing(false); setPages([0]); }} /><Button title="Acompanhando" secondary={!following} onPress={() => { setFollowing(true); setPages([0]); }} /></View>
    <Message text={remote.error} />{remote.loading && <Text style={s.small}>Carregando passeios…</Text>}
    {remote.data?.itens.map(item => <View key={item.id} style={[s.secondary, { padding: 18, borderRadius: 18, gap: 10 }]}>
      <Text style={s.cardTitle}>{item.titulo}</Text><Text style={s.small}>Por {item.autor.nome} · {item.cidade} · {item.paradas} paradas</Text>
      <Button title="Conhecer passeio" secondary onPress={() => router.push({ pathname: '/publications/[id]', params: { id: item.id } })} />
    </View>)}
    {remote.data?.itens.length === 0 && <Text style={s.text}>{following ? 'Ainda não há passeios públicos das pessoas que você acompanha.' : 'Ainda não há passeios publicados. Compartilhe um em Meus rolês.'}</Text>}
    {!!remote.data?.proximo && <Button title="Mais passeios" secondary onPress={() => setPages([...pages, remote.data!.proximo!])} />}
    {pages.length > 1 && <Button title="Página anterior" secondary onPress={() => setPages(pages.slice(0, -1))} />}
    <Button title="Conhecer pessoas" secondary onPress={() => router.push('/people')} /><Button title="Atualizar" secondary disabled={remote.loading} onPress={remote.reload} />
  </ScrollView>;
}
export function TextsScreen({ token, type, target }: { token: string; type: 'COMENTARIO' | 'AVALIACAO'; target: string }) {
  const [pages, setPages] = useState<number[]>([0]), [text, setText] = useState(''), [score, setScore] = useState('5'), [editing, setEditing] = useState(false);
  const scroll = useRef<ScrollView>(null); const focus = useFormKeyboard(scroll);
  const path = `/comunidade/textos/${type}/${target}`;
  const remote = useSocialResource<Entries>(`${path}?apos=${pages[pages.length - 1]}`, token);
  const action = useAction(token, remote.generation), review = type === 'AVALIACAO';
  return <FormKeyboardView><ScrollView ref={scroll} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>{review ? 'Avaliações de visitantes' : 'Comentários'}</Text><Message text={remote.error || action.message} />
    {remote.loading && <Text style={s.small}>Carregando…</Text>}
    {remote.data && <Text style={s.subtitle}>{remote.data.total} {review ? 'avaliações' : 'comentários'}{remote.data.media != null ? ` · ${remote.data.media.toFixed(1)} / 5` : ''}</Text>}
    <Button title={editing ? 'Fechar edição' : review ? 'Escrever ou atualizar minha avaliação' : 'Escrever ou atualizar meu comentário'} secondary onPress={() => setEditing(!editing)} />
    {editing && <>
      <Text style={s.small}>Seu perfil precisa estar público. Um relato por pessoa, que pode ser atualizado.{review ? ' Avalie após concluir a parada. A visita é autodeclarada, não comprovada pelo app.' : ''}</Text>
      {review && <Field label="Nota de 1 a 5" value={score} setValue={setScore} keyboardType="number-pad" onFocus={focus} />}
      <TextInput accessibilityLabel="Relato" value={text} onChangeText={setText} maxLength={1000} multiline onFocus={focus} style={[s.field, { minHeight: 100, textAlignVertical: 'top' }]} />
      <Button title="Publicar relato" busy={action.busy} disabled={!text.trim() || (review && !/^[1-5]$/.test(score))} onPress={() => { void action.run(path, 'PUT', { texto: text, nota: review ? Number(score) : null }, () => { setEditing(false); setText(''); remote.reload(); }); }} />
      <Button title="Configurar meu perfil público" secondary onPress={() => router.push('/social-profile')} />
    </>}
    {remote.data?.itens.map(item => <View key={item.id} style={[s.secondary, { padding: 16, borderRadius: 16, gap: 10 }]}>
      <Text style={s.cardTitle}>{item.autor.nome}{item.nota != null ? ` · ${item.nota}/5` : ''}</Text><Text style={s.text}>{item.texto}</Text>
      <Button title="Ver perfil" secondary onPress={() => router.push({ pathname: '/people/[id]', params: { id: item.autor.id } })} />
      {item.meu && <><Button title="Editar meu relato" secondary onPress={() => { setText(item.texto); setScore(String(item.nota ?? 5)); setEditing(true); scroll.current?.scrollTo({ y: 0 }); }} /><Button title="Excluir meu relato" secondary busy={action.busy} onPress={() => { void action.run(path, 'DELETE', undefined, remote.reload); }} /></>}
      <Button title="Denunciar" secondary onPress={() => router.push({ pathname: '/report', params: { tipo: 'TEXTO', alvo: String(item.id) } })} />
    </View>)}
    {remote.data?.itens.length === 0 && <Text style={s.small}>Nenhum relato nesta página.</Text>}
    {!!remote.data?.proximo && <Button title="Mais relatos" secondary onPress={() => setPages([...pages, remote.data!.proximo!])} />}
    {pages.length > 1 && <Button title="Página anterior" secondary onPress={() => setPages(pages.slice(0, -1))} />}
    <Button title="Atualizar relatos" secondary disabled={remote.loading} onPress={remote.reload} />
  </ScrollView></FormKeyboardView>;
}
export function ReportScreen({ token, type, target }: { token: string; type: string; target: string }) {
  const [reason, setReason] = useState(''), [sent, setSent] = useState(false);
  const remote = useSocialResource('/auth/me', token), action = useAction(token, remote.generation);
  const scroll = useRef<ScrollView>(null), focus = useFormKeyboard(scroll);
  return <FormKeyboardView><ScrollView ref={scroll} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>Denunciar conteúdo</Text><Text style={s.subtitle}>Explique o problema. A denúncia será analisada pela equipe.</Text>
    <Message text={remote.error || action.message} />
    {sent ? <Text style={s.text}>Denúncia registrada. Seu nome não será mostrado ao autor do conteúdo.</Text> : <>
      <TextInput accessibilityLabel="Motivo da denúncia" multiline maxLength={1000} value={reason} onChangeText={setReason} onFocus={focus} style={[s.field, { minHeight: 120 }]} />
      <Button title="Enviar denúncia" disabled={!reason.trim() || remote.loading} busy={action.busy} onPress={() => { void action.run('/comunidade/denuncias', 'POST', { tipo: type, alvo: target, motivo: reason }, () => setSent(true)); }} />
    </>}
  </ScrollView></FormKeyboardView>;
}
type Report = { id: number; tipo: string; alvo: string; motivo: string; conteudo: string };
export function ModerationScreen({ token }: { token: string }) {
  const [after, setAfter] = useState(0), [reason, setReason] = useState(''), [selected, setSelected] = useState<number | null>(null);
  const remote = useSocialResource<{ itens: Report[] }>(`/comunidade/denuncias?apos=${after}`, token), action = useAction(token, remote.generation);
  const scroll = useRef<ScrollView>(null), focus = useFormKeyboard(scroll);
  return <FormKeyboardView><ScrollView ref={scroll} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>Analisar denúncias</Text><Message text={remote.error || action.message} />
    {remote.data?.itens.map(item => <View key={item.id} style={[s.secondary, { padding: 16, borderRadius: 16, gap: 10 }]}>
      <Text style={s.label}>{item.tipo}</Text><Text style={s.text}>{item.conteudo}</Text><Text style={s.small}>Motivo: {item.motivo}</Text>
      {item.tipo === 'PUBLICACAO' && <Button title="Ver publicação e fotos" secondary onPress={() => router.push({ pathname: '/publications/[id]', params: { id: item.alvo } })} />}
      <Button title="Analisar" secondary onPress={() => { setSelected(item.id); setReason(''); }} />
      {selected === item.id && <><Field label="Justificativa da decisão" value={reason} setValue={setReason} onFocus={focus} />
        {[true, false].map(hide => <Button key={String(hide)} title={hide ? 'Ocultar conteúdo' : 'Manter conteúdo'} secondary disabled={!reason.trim()} busy={action.busy} onPress={() => { void action.run(`/comunidade/denuncias/${item.id}`, 'PUT', { ocultar: hide, justificativa: reason }, () => { setSelected(null); remote.reload(); }); }} />)}
      </>}
    </View>)}
    {remote.data?.itens.length === 0 && <Text style={s.text}>Nenhuma denúncia pendente nesta página.</Text>}
    {remote.data?.itens.length === 20 && <Button title="Próximas denúncias" secondary onPress={() => { setAfter(remote.data!.itens[19].id); setSelected(null); }} />}
    <Button title="Atualizar desde o início" secondary onPress={() => { setAfter(0); setSelected(null); remote.reload(); }} />
  </ScrollView></FormKeyboardView>;
}
export function ItineraryCredits({ token, id, publication }: { token: string; id: number | string; publication?: boolean }) {
  const remote = useSocialResource<{ creditos: { autorNome: string; titulo: string }[] }>(`/comunidade/${publication ? 'origem' : 'creditos'}/${id}`, token);
  return <>{remote.data?.creditos.map((credit, i) => <Text key={i} style={s.small}>Inspirado em “{credit.titulo}”, de {credit.autorNome}.</Text>)}</>;
}
