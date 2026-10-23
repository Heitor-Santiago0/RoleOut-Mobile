import { useCallback, useState } from 'react';
import { Linking, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { useAuth } from './AuthContext';
import { ApiError, request } from './api';
import { notifications, registerPush, serializePush, type PushStatus } from './pushNotifications';
import { Button, Message } from './ui';
import { styles as s } from './theme';

export function PushSettings({ token, onChanged }: { token: string; onChanged?: () => void }) {
  const {expire}=useAuth();
  const [status,setStatus]=useState<PushStatus|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const [retry,setRetry]=useState(0);
  useFocusEffect(useCallback(()=>{
    let active=true;
    request<PushStatus>('/notificacoes',{token}).then(value=>{if(active){setStatus(value);setError('');}}).catch(e=>{if(active)setError(e.message);});
    return ()=>{active=false;};
  // retry is the manual refresh trigger.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[token,retry]));
  async function toggle() {
    if(!status)return;
    setBusy(true);setError('');
    try {
      await serializePush(async()=>{
        if(status.ativo) {await request('/notificacoes',{method:'DELETE',token});setStatus({...status,ativo:false});}
        else {const value=await registerPush(token,true);if(value)setStatus(value);}
      });
    } catch(e) {if(e instanceof ApiError && e.status===401)void expire();else setError(e instanceof Error?e.message:'Não foi possível atualizar os avisos.');}
    finally {onChanged?.();setBusy(false);}
  }
  const supported=!!notifications();
  return <View style={[s.secondary,{padding:16,borderRadius:14,gap:10}]}>
    <Text style={s.cardTitle}>Avisos de alterações</Text>
    <Text style={s.text}>Receba avisos de mudanças de datas, horários ou cancelamentos nas paradas dos seus rolês salvos, mesmo com o app fechado.</Text>
    <Text style={s.small}>Opcional, neste celular e nesta sessão. Ao sair da conta ou expirar a sessão, ative novamente após entrar. Os avisos não alteram seu planejamento.</Text>
    {!supported && <Text style={s.small}>É necessário instalar o novo APK Android com suporte a notificações.</Text>}
    {status && !status.envioConfigurado && <Text style={s.small}>Envio ainda em preparação: falta concluir a configuração do Firebase e ativar o serviço no servidor.</Text>}
    {status?.ativo && <Text style={s.label}>Avisos autorizados nesta sessão.</Text>}
    {status?.ultimoErro && <Text style={s.small}>Houve uma falha no envio. Consulte também as atualizações em Meus rolês.</Text>}
    {status && (status.ativo || (supported && status.envioConfigurado)) && <Button secondary busy={busy} title={status.ativo?'Desativar avisos neste celular':'Ativar avisos neste celular'} onPress={()=>void toggle()}/>}
    {!!error && <><Message text={error}/><Button secondary busy={busy} title={status?'Repetir tentativa':'Atualizar situação dos avisos'} onPress={()=>{if(status)void toggle();else setRetry(v=>v+1);}}/></>}
    {supported && <Button secondary title="Permissões no Android" onPress={()=>{void Linking.openSettings().catch(()=>setError('Abra as configurações do Android para revisar as permissões.'));}}/>}
    <Text style={s.small}>Os avisos precisam de internet e do servidor ligado. O Android pode atrasar ou impedir a entrega. Confira seus horários antes de sair.</Text>
  </View>;
}
