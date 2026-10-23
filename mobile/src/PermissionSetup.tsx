import { useCallback, useRef, useState } from 'react';
import { AppState, Linking, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import * as Location from 'expo-location';
import { request } from './api';
import { readToken } from './session';
import { notifications, registerPush, serializePush, type PushStatus } from './pushNotifications';
import { serializeTravel, travelManager, travelSession, travelTaskName } from './travelControl';
import { startTravel } from './startTravel';
import { activateFeatures, boundedPosition, type ActivationResult } from './permissionActivation';
import type { SuggestionSettings } from './automaticSuggestions';
import { Button } from './ui';
import { styles as s } from './theme';

export function PermissionSetup({ token, revision = 0 }: { token: string; revision?: number }) {
  const generation=useRef(0), running=useRef(false);
  const [busy,setBusy]=useState(false),[results,setResults]=useState<ActivationResult[]>([]),[status,setStatus]=useState('');
  const [complete,setComplete]=useState(false),[checking,setChecking]=useState(true);
  const refreshId=useRef(0);
  const refresh=useCallback(async (version:number)=>{
    const id=++refreshId.current;
    const valid=()=>generation.current===version && refreshId.current===id;
    if(valid())setChecking(true);
    try {
      const location=await Location.getForegroundPermissionsAsync(), n=notifications();
      const notice=!!n && (await n.getPermissionsAsync()).granted;
      if(valid())setStatus(`Localização: ${location.granted?'autorizada':'não autorizada'} · Notificações: ${notice?'autorizadas':'não autorizadas'}`);
      const [suggestions,push,background,consent]=await Promise.all([
        request<SuggestionSettings>('/sugestoes-automaticas',{token}),
        request<PushStatus>('/notificacoes',{token}),
        Location.getBackgroundPermissionsAsync(),travelSession(),
      ]);
      const travel=!!travelManager() && consent===token && await Location.hasStartedLocationUpdatesAsync(travelTaskName);
      if(valid())setComplete(location.granted && notice && suggestions.ativo && suggestions.viagens && !!suggestions.cidadeBase?.trim() && push.ativo && background.granted && travel);
    }catch {if(valid()){setComplete(false);setStatus('Não foi possível conferir todos os recursos. Tente conferir novamente.');}}
    finally {if(valid())setChecking(false);}
  },[token]);
  useFocusEffect(useCallback(()=>{
    const version=++generation.current;
    void refresh(version);const listener=AppState.addEventListener('change',value=>{if(value==='active')void refresh(version);});
    return()=>{generation.current++;listener.remove();};
  // Invalidate in-flight activation when the account session changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[refresh,token,revision]));
  async function setup() {
    if(running.current || complete || checking)return;
    running.current=true;setBusy(true);setResults([]);
    const version=generation.current;
    const current=async()=>generation.current===version && await readToken()===token;
    try {
      const activated=await activateFeatures([
        {key:'location',success:'Localização autorizada.',pending:'Localização não autorizada. A busca por cidade continua disponível.',run:async()=> (await Location.requestForegroundPermissionsAsync()).granted},
        {key:'suggestions',success:'Sugestões ativadas.',pending:'Informe sua cidade em Sugestões automáticas para concluir.',run:async()=>{
          if(!(await Location.getForegroundPermissionsAsync()).granted)return false;
          const previous=await request<SuggestionSettings>('/sugestoes-automaticas',{token});
          let city=previous.cidadeBase;
          if(!city) {
            const point=await boundedPosition(Location.getCurrentPositionAsync({accuracy:Location.Accuracy.Balanced}));
            if(!await current())return false;
            const addresses=await boundedPosition(Location.reverseGeocodeAsync(point.coords));
            city=addresses[0]?.city??addresses[0]?.subregion??null;
          }
          if(!city || !await current())return false;
          const saved=await request<SuggestionSettings>('/sugestoes-automaticas',{method:'PUT',token,body:{...previous,ativo:true,viagens:true,cidadeBase:city}});
          return saved.ativo && saved.viagens;
        }},
        {key:'push',success:'Avisos de alterações ativados.',pending:'Avisos de alterações ainda não confirmados.',run:()=>serializePush(async()=>{
          if(!await current())return false;
          const saved=await registerPush(token,true,()=>generation.current===version);
          return saved?.ativo===true;
        })},
        {key:'travel',success:'Avisos em viagens ativados.',pending:'Avisos em viagens ainda não confirmados.',run:()=>serializeTravel(async()=>{
          if(!await current())return false;
          return startTravel(token,()=>generation.current===version);
        })},
      ],current,setResults);
      if(await current())setComplete(activated.length===4 && activated.every(result=>result.enabled));
    } finally {
      running.current=false;
      setBusy(false);
      if(generation.current===version)void refresh(version);
    }
  }
  return <View style={{gap:10}}>
    <Text style={s.cardTitle}>Permissões do aplicativo</Text>
    <Text style={s.small}>Ative os recursos em uma sequência. O Android pedirá sua autorização. Para avisos em viagens, usamos localização mesmo fora do app, sem guardar coordenadas, com aviso permanente do Android. Você pode desligar cada recurso abaixo ou nas permissões do aparelho.</Text>
    <Text style={s.small}>{status}</Text>
    {!travelManager() && <Text style={s.small}>Avisos em viagens precisam do novo APK. Os demais recursos continuam disponíveis.</Text>}
    {complete ? <Text accessibilityRole="alert" style={s.label}>✓ Recursos ativados. Você pode gerenciar cada opção abaixo.</Text> : <Button title={checking ? "Conferindo recursos…" : results.length ? "Concluir ativação" : "Autorizar e ativar recursos"} disabled={checking} busy={busy} onPress={()=>void setup().catch(()=>setStatus('Não foi possível conferir a sessão. Entre novamente para configurar.'))} />} 
    {!complete && results.map(result=><Text key={result.key} accessibilityRole="alert" style={s.small}>{result.enabled?'✓':'•'} {result.message}</Text>)}
    <Button title="Permissões no aparelho" secondary onPress={()=>void Linking.openSettings().catch(()=>setStatus('Abra as configurações do Android.'))} />
  </View>;
}
