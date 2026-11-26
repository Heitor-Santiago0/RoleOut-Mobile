import { useRef, useState } from 'react';
import { Image, ScrollView, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { useSocialResource } from './useSocialResource';
import { styles as s } from './theme';
import { Button, Message } from './ui';
import { FormKeyboardView } from './FormKeyboardView';
import { PublicationContent } from './PublicationContent';
import { publicationError, publicationInput, publicationVersion, type OwnPublication } from './publication';

export function PublicationEditor({ token, routeId }: { token: string; routeId: number }) {
  const remote = useSocialResource<OwnPublication>(`/minhas-publicacoes/${routeId}`, token, false);
  if (!remote.data) return <ScrollView contentContainerStyle={s.content}><Text style={s.title}>Compartilhar passeio</Text>
    {remote.loading && <Text style={s.small}>Carregando roteiro…</Text>}<Message text={remote.error} />
    {!!remote.error && <Button title="Tentar novamente" onPress={remote.reload} />}</ScrollView>;
  return <PublicationForm token={token} routeId={routeId} initial={remote.data} remote={remote} />;
}
function PublicationForm({ token, routeId, initial, remote }: { token: string; routeId: number; initial: OwnPublication; remote: ReturnType<typeof useSocialResource<OwnPublication>> }) {
  const { expire } = useAuth();
  const [draft, setDraft] = useState(initial), [saved, setSaved] = useState(initial);
  const [preview, setPreview] = useState(false), [removeConfirm, setRemoveConfirm] = useState(false);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const lock = useRef(false);
  async function persist(action: 'draft' | 'publish' | 'hide' | 'delete') {
    if (lock.current) return;
    if (action === 'draft' || action === 'publish') { const invalid = publicationError(draft); if (invalid) { setError(invalid); return; } }
    const generation = remote.generation.current;
    lock.current = true; setBusy(true); setError(''); setNotice('');
    try {
      const result = await request<OwnPublication>(`/minhas-publicacoes/${routeId}${action === 'hide' ? '/ocultar' : ''}`, {
        token, method: action === 'delete' ? 'DELETE' : 'PUT', timeoutMs: 60000,
        body: action === 'hide' || action === 'delete' ? publicationVersion(saved) : publicationInput(draft, action === 'publish'),
      });
      if (generation !== remote.generation.current) return;
      if (action === 'delete') { remote.reload(); return; }
      setDraft(result); setSaved(result); setPreview(false); setRemoveConfirm(false);
      setNotice(action === 'publish' ? 'Passeio publicado no seu perfil.' : action === 'hide' ? 'Publicação ocultada.' : 'Rascunho privado salvo.');
    } catch (e) {
      if (generation !== remote.generation.current) return;
      if (e instanceof ApiError && e.status === 401) void expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível salvar.');
    } finally { lock.current = false; if (generation === remote.generation.current) setBusy(false); }
  }
  async function pickPhoto() {
    if (lock.current || draft.fotos.length >= 3) return;
    const generation = remote.generation.current;
    lock.current = true; setBusy(true); setError('');
    try {
      // Lazy imports keep older development APKs usable; a missing native module is explained on tap.
      const picker = await import('expo-image-picker');
      const result = await picker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsMultipleSelection: false, quality: 0.8, exif: false });
      if (result.canceled || generation !== remote.generation.current) return;
      const asset = result.assets[0];
      const { ImageManipulator, SaveFormat } = await import('expo-image-manipulator');
      const context = ImageManipulator.manipulate(asset.uri);
      if (Math.max(asset.width, asset.height) > 1280) context.resize(asset.width >= asset.height ? { width: 1280 } : { height: 1280 });
      const rendered = await context.renderAsync();
      const photo = await rendered.saveAsync({ format: SaveFormat.JPEG, compress: 0.75, base64: true });
      if (generation !== remote.generation.current) return;
      if (!photo.base64 || photo.base64.length > 2796204) throw new Error('Escolha uma foto menor, de até 2 MB.');
      setDraft(value => ({ ...value, fotos: [...value.fotos, { base64: photo.base64!, legenda: '' }] })); setNotice('');
    } catch (e) {
      if (generation === remote.generation.current) setError(e instanceof Error && /native module|ExpoImage/i.test(e.message)
        ? 'Para escolher fotos, instale o novo APK. Você pode salvar o relato sem fotos por enquanto.'
        : 'Não foi possível abrir essa foto. Tente outra imagem e confira a permissão de fotos.');
    } finally { lock.current = false; if (generation === remote.generation.current) setBusy(false); }
  }
  return <FormKeyboardView><ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={s.content}>
    <Text style={s.title}>{preview ? 'Prévia da publicação' : 'Compartilhar passeio'}</Text>
    <Text style={s.small}>{saved.publico ? 'Publicado · aparece enquanto seu perfil estiver público' : 'Privado · só você pode ver'}</Text>
    <Message text={error} />{!!notice && <Text style={s.label} accessibilityRole="alert">{notice}</Text>}
    {preview ? <>
      <PublicationContent value={draft} />
      <Text style={s.small}>Ao publicar, pessoas com conta no RolêOut poderão ver estes textos, fotos e paradas com seu nome público. Confira se suas fotos e seu relato não expõem informações que deseja manter privadas.</Text>
      <Button title={saved.publico ? 'Confirmar alterações públicas' : 'Confirmar publicação'} busy={busy} onPress={() => void persist('publish')} />
      <Button title="Voltar à edição" secondary disabled={busy} onPress={() => setPreview(false)} />
    </> : <>
      <Text style={s.label}>Título</Text><TextInput accessibilityLabel="Título da publicação" style={s.field} value={draft.titulo} maxLength={120} editable={!busy} onChangeText={titulo => setDraft({ ...draft, titulo })} />
      <Text style={s.label}>Seu relato</Text><TextInput accessibilityLabel="Relato do passeio" style={[s.field, { minHeight: 140, textAlignVertical: 'top' }]} multiline maxLength={3000} editable={!busy} value={draft.relato} onChangeText={relato => setDraft({ ...draft, relato })} placeholder="O que mais gostou? Que dica deixaria para outra pessoa?" placeholderTextColor="#667085" />
      <Text style={s.label}>Fotos · {draft.fotos.length}/3</Text>
      {draft.fotos.map((photo, index) => <View key={index} style={{ gap: 8 }}>
        <Image source={{ uri: `data:image/jpeg;base64,${photo.base64}` }} accessibilityLabel={`Foto ${index + 1}`} style={{ width: '100%', height: 200, borderRadius: 16 }} resizeMode="contain" />
        <TextInput accessibilityLabel={`Legenda da foto ${index + 1}`} style={s.field} value={photo.legenda} editable={!busy} maxLength={200} placeholder="Legenda (opcional)" placeholderTextColor="#667085" onChangeText={legenda => setDraft({ ...draft, fotos: draft.fotos.map((p, i) => i === index ? { ...p, legenda } : p) })} />
        <Button title={`Remover foto ${index + 1}`} secondary disabled={busy} onPress={() => setDraft({ ...draft, fotos: draft.fotos.filter((_, i) => i !== index) })} />
      </View>)}
      {draft.fotos.length < 3 && <Button title="Escolher foto" secondary disabled={busy} onPress={() => void pickPhoto()} />}
      <Text style={s.small}>A publicação inclui cidade e paradas, sem sua data, horários, participantes ou orçamento. O roteiro original continua privado. As paradas compartilhadas ficam registradas como estavam ao criar o rascunho.</Text>
      <Button title="Revisar antes de publicar" disabled={busy} onPress={() => { const invalid = publicationError(draft); if (invalid) setError(invalid); else { setError(''); setPreview(true); } }} />
      <Button title="Salvar como rascunho privado" secondary busy={busy} onPress={() => void persist('draft')} />
      {saved.publico && <Button title="Ocultar publicação agora" secondary disabled={busy} onPress={() => void persist('hide')} />}
      {saved.id && <Button title="Ver publicação" secondary disabled={busy || !saved.publico} onPress={() => router.push({ pathname: '/publications/[id]', params: { id: saved.id! } })} />}
      {saved.id && <Button title="Excluir publicação" secondary disabled={busy} onPress={() => setRemoveConfirm(true)} />}
      {removeConfirm && <View style={{ gap: 8 }}><Text style={s.text}>Excluir fotos e relato publicados? Seu roteiro privado será mantido.</Text><Button title="Confirmar exclusão da publicação" disabled={busy} onPress={() => void persist('delete')} /><Button title="Cancelar" secondary disabled={busy} onPress={() => setRemoveConfirm(false)} /></View>}
      <Button title="Configurar meu perfil público" secondary disabled={busy} onPress={() => router.push('/social-profile')} />
      <Text style={s.small}>Salve o rascunho antes de sair desta tela para manter seus textos e fotos.</Text>
      {!!error && <Button title="Recarregar versão salva" secondary disabled={busy} onPress={remote.reload} />}
    </>}
  </ScrollView></FormKeyboardView>;
}
