import { useRef, useState } from 'react';
import { Image, ScrollView, Switch, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { FormKeyboardView } from './FormKeyboardView';
import { Button, Message } from './ui';
import { styles as s } from './theme';
import { useSocialResource } from './useSocialResource';
import { profileError, profileInput, type OwnProfile } from './socialProfile';
import type { ProfileDetails } from './ProfileHero';
export function SocialProfileEditor({ token }: { token: string }) {
  const profile = useSocialResource<OwnProfile>('/perfil-social', token, false);
  const details = useSocialResource<ProfileDetails>('/perfil-detalhes', token, false);
  if (!profile.data || !details.data) return <ScrollView contentContainerStyle={s.content}><Text style={s.title}>Editar perfil</Text><Message text={profile.error || details.error} /><Text style={s.small}>{profile.loading || details.loading ? 'Carregando…' : ''}</Text>{!!(profile.error || details.error) && <Button title="Tentar novamente" onPress={() => { profile.reload(); details.reload(); }} />}</ScrollView>;
  return <ProfileForm token={token} initial={profile.data} details={details.data} />;
}
function ProfileForm({ token, initial, details }: { token: string; initial: OwnProfile; details: ProfileDetails }) {
  const { expire, session } = useAuth();
  const [draft,setDraft]=useState({ ...initial, nome: initial.nome || session?.conta.nome || '' });
  const [photo,setPhoto]=useState(details.fotoBase64), [interests,setInterests]=useState(details.interessesPublicos);
  const [busy,setBusy]=useState(false), [error,setError]=useState(''), [notice,setNotice]=useState('');
  const lock=useRef(false);
  async function pickPhoto(){
    if(lock.current)return; lock.current=true;setBusy(true);setError('');
    try {
      const picker=await import('expo-image-picker');
      const result=await picker.launchImageLibraryAsync({mediaTypes:['images'],allowsEditing:true,aspect:[1,1],quality:0.8,exif:false});
      if(result.canceled)return;
      const {ImageManipulator,SaveFormat}=await import('expo-image-manipulator');
      const asset=result.assets[0], context=ImageManipulator.manipulate(asset.uri);
      context.resize(asset.width>=asset.height?{width:512}:{height:512});
      const image=await context.renderAsync(); const saved=await image.saveAsync({format:SaveFormat.JPEG,compress:0.8,base64:true});
      if(!saved.base64 || saved.base64.length>2796204)throw new Error('Escolha uma foto menor.');
      setPhoto(saved.base64);setNotice('');
    }catch(e){setError(e instanceof Error?e.message:'Não foi possível abrir a foto.');}
    finally{lock.current=false;setBusy(false);}
  }
  async function save(){
    if(lock.current)return;const invalid=profileError(draft);if(invalid){setError(invalid);return;}
    lock.current=true;setBusy(true);setError('');setNotice('');let profileSaved=false;
    try {
      const value=await request<OwnProfile>('/perfil-social',{token,method:'PUT',body:profileInput(draft,draft.publico)});
      setDraft(value);profileSaved=true;
      await request('/perfil-detalhes',{token,method:'PUT',body:{fotoBase64:photo,interessesPublicos:interests}});
      setNotice('Perfil salvo.');
    }catch(e){if(e instanceof ApiError&&e.status===401)void expire();else setError((profileSaved?'Nome, bio e visibilidade salvos. Não foi possível salvar foto e interesses: ':'')+(e instanceof Error?e.message:'Tente novamente.'));}
    finally{lock.current=false;setBusy(false);}
  }
  return <FormKeyboardView><ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>Editar perfil</Text><Message text={error} />{!!notice&&<Text style={s.label} accessibilityRole="alert">{notice}</Text>}
    {photo&&<Image source={{uri:`data:image/jpeg;base64,${photo}`}} accessibilityLabel="Minha foto" style={{width:96,height:96,borderRadius:48,alignSelf:'center'}} />}
    <Button title={photo?'Trocar foto':'Adicionar foto'} secondary disabled={busy} onPress={()=>void pickPhoto()} />
    {photo&&<Button title="Remover foto" secondary disabled={busy} onPress={()=>setPhoto(null)} />}
    <Text style={s.label}>Nome</Text><TextInput accessibilityLabel="Nome público" style={s.field} value={draft.nome} maxLength={80} editable={!busy} onChangeText={nome=>setDraft({...draft,nome})} />
    <Text style={s.label}>Bio</Text><TextInput accessibilityLabel="Sobre mim" multiline style={[s.field,{minHeight:90,textAlignVertical:'top',paddingVertical:12}]} value={draft.apresentacao} maxLength={500} editable={!busy} onChangeText={apresentacao=>setDraft({...draft,apresentacao})} placeholder="Um pouco sobre você" />
    <View style={s.row}><Text style={[s.text,{flex:1}]}>Perfil público</Text><Switch accessibilityLabel="Perfil público" value={draft.publico} disabled={busy} onValueChange={publico=>setDraft({...draft,publico})} /></View>
    <View style={s.row}><Text style={[s.text,{flex:1}]}>Mostrar meus interesses</Text><Switch accessibilityLabel="Mostrar meus interesses" value={interests} disabled={busy} onValueChange={setInterests} /></View>
    <Text style={s.small}>Com perfil público, outras pessoas veem sua foto, bio e rolês publicados. Seus roteiros privados continuam só seus.</Text>
    <Button title="Salvar perfil" busy={busy} onPress={()=>void save()} />
    <Button title="Escolher interesses" secondary disabled={busy} onPress={()=>router.push('/preferences')} />
  </ScrollView></FormKeyboardView>;
}
