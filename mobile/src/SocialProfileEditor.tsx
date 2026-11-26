import { useRef, useState } from 'react';
import { ScrollView, Switch, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { FormKeyboardView } from './FormKeyboardView';
import { Button, Message } from './ui';
import { styles as s } from './theme';
import { useSocialResource } from './useSocialResource';
import { profileError, profileInput, type OwnProfile } from './socialProfile';

export function SocialProfileEditor({ token }: { token: string }) {
  const remote = useSocialResource<OwnProfile>('/perfil-social', token, false);
  if (!remote.data) return <ScrollView contentContainerStyle={s.content}>
    <Text style={s.title}>Meu perfil público</Text>
    {remote.loading && <Text style={s.small}>Carregando perfil…</Text>}
    <Message text={remote.error} />
    {!!remote.error && <Button title="Tentar novamente" secondary onPress={remote.reload} />}
  </ScrollView>;
  return <ProfileForm token={token} initial={remote.data} remote={remote} />;
}
function ProfileForm({ token, initial, remote }: { token: string; initial: OwnProfile; remote: ReturnType<typeof useSocialResource<OwnProfile>> }) {
  const { expire } = useAuth();
  const [draft, setDraft] = useState<OwnProfile | null>(initial), [saved, setSaved] = useState<OwnProfile | null>(initial);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const saving = useRef(false);
  async function save(hide = false) {
    const value = hide ? saved : draft;
    if (!value || saving.current) return;
    const invalid = profileError(value); if (invalid) { setError(invalid); return; }
    const version = remote.generation.current;
    saving.current = true; setBusy(true); setError(''); setNotice('');
    try {
      const result = await request<OwnProfile>('/perfil-social', { token, method: 'PUT', body: profileInput(value, hide ? false : value.publico) });
      if (remote.generation.current !== version) return;
      setDraft(result); setSaved(result); setNotice(result.publico ? 'Perfil publicado.' : 'Perfil salvo como privado.');
    } catch (e) {
      if (remote.generation.current !== version) return;
      if (e instanceof ApiError && e.status === 401) void expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível salvar.');
    } finally { saving.current = false; setBusy(false); }
  }
  return <FormKeyboardView><ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={s.content}>
    <Text style={s.title}>Meu perfil público</Text>
    <Text style={s.subtitle}>Escolha como quer aparecer para outras pessoas no RolêOut.</Text>
    {remote.loading && <Text style={s.small}>Carregando perfil…</Text>}
    <Message text={remote.error || error} />
    {!!notice && <Text accessibilityRole="alert" style={s.label}>{notice}</Text>}
    {draft && <>
      <Text style={s.label}>{saved?.publico ? 'Publicado para pessoas com conta no RolêOut' : 'Privado — só você pode ver'}</Text>
      <View style={{ gap: 8 }}><Text style={s.label}>Nome público</Text><TextInput accessibilityLabel="Nome público" style={s.field} value={draft.nome} maxLength={80} editable={!busy} onChangeText={nome => { setDraft({ ...draft, nome }); setNotice(''); }} placeholder="Como quer ser conhecido?" placeholderTextColor="#667085" /></View>
      <View style={{ gap: 8 }}><Text style={s.label}>Sobre mim</Text><TextInput accessibilityLabel="Sobre mim" style={[s.field, { minHeight: 120, paddingVertical: 12, textAlignVertical: 'top' }]} multiline maxLength={500} editable={!busy} value={draft.apresentacao} onChangeText={apresentacao => { setDraft({ ...draft, apresentacao }); setNotice(''); }} placeholder="Que tipos de passeio você gosta de fazer?" placeholderTextColor="#667085" /><Text style={s.small}>{draft.apresentacao.length}/500</Text></View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Text style={[s.text, { flex: 1 }]}>Mostrar meu perfil para outras pessoas</Text><Switch accessibilityLabel="Mostrar meu perfil para outras pessoas" disabled={busy} value={draft.publico} onValueChange={publico => { setDraft({ ...draft, publico }); setNotice('Salve para aplicar esta escolha.'); }} /></View>
      <Text style={s.small}>Seu nome, apresentação e passeios que você publicar ficam visíveis. E-mail, localização, preferências e planejamento privado não são compartilhados. Ocultar o perfil esconde também as publicações; republicá-lo volta a mostrar as que não foram ocultadas individualmente.</Text>
      <Button title={draft.publico ? 'Salvar perfil público' : 'Salvar perfil privado'} busy={busy} onPress={() => void save()} />
      {saved?.publico && saved.id && <>
        <Button title="Ver como outras pessoas" secondary disabled={busy} onPress={() => router.push({ pathname: '/people/[id]', params: { id: saved.id! } })} />
        <Button title="Tornar privado agora" secondary disabled={busy} onPress={() => void save(true)} />
      </>}
    </>}
    {!!(remote.error || error) && <Button title="Recarregar perfil salvo" secondary disabled={busy} onPress={remote.reload} />}
    <Button title="Conhecer pessoas" secondary disabled={busy} onPress={() => router.push('/people')} />
  </ScrollView></FormKeyboardView>;
}
