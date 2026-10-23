import { useCallback, useState } from 'react';
import { Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { Button, Message } from './ui';
import { styles as s } from './theme';
type Check = { verificadoEm: string; paradasVerificadas: number; alertas: { roleId: number; nome: string; codigo: string; mensagem: string }[] };
type Route = { id: number; nome: string; verificacao: Check };
export function ScheduleAlerts({ token, id }: { token: string; id?: number }) {
  const { expire } = useAuth();
  const [routes,setRoutes]=useState<Route[] | null>(null);
  const [busy,setBusy]=useState(false); const [error,setError]=useState(''); const [retry,setRetry]=useState(0);
  useFocusEffect(useCallback(() => {
    let active=true; setBusy(true); setError(''); setRoutes(null);
    (async () => {
      const result = id === undefined ? (await request<{itens:Route[]}>('/roteiros/alertas',{token})).itens
        : [{id,nome:'Este rolê',verificacao:await request<Check>(`/roteiros/${id}/verificar`,{token})}];
      if(active) setRoutes(result);
    })().catch(e => {
      if(!active) return;
      if(e instanceof ApiError && e.status===401) void expire();
      else setError('Não foi possível conferir alterações agora. Sem conexão, os horários salvos podem estar desatualizados.');
    }).finally(() => { if(active) setBusy(false); });
    return () => {active=false;};
  // retry intentionally reruns the focused-screen check after a manual refresh.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[token,id,retry,expire]));
  const alerts=routes?.filter(r=>r.verificacao.alertas.length);
  return <View style={[s.secondary,{padding:16,borderRadius:14,gap:10}]}>
    <Text style={s.cardTitle}>Atualizações dos horários</Text>
    <Button secondary title="Verificar alterações" busy={busy} onPress={()=>setRetry(v=>v+1)} />
    <Message text={error}/>
    {routes && !alerts?.length && <Text style={s.small}>Nenhuma alteração ou incompatibilidade detectada nas paradas pendentes com os dados disponíveis. Confirme informações não cadastradas e disponibilidade com o responsável.</Text>}
    {alerts?.map(route=><View key={route.id} style={{gap:8}}>
      <Text style={s.label}>{route.nome}</Text>
      {route.verificacao.alertas.map(a=><Text accessibilityRole="alert" key={`${a.roleId}:${a.codigo}`} style={s.text}>{a.nome}: {a.mensagem}</Text>)}
      {id===undefined ? <Button secondary title={`Revisar ${route.nome}`} onPress={()=>router.push({pathname:'/itineraries/[id]',params:{id:route.id}})}/> :
        [...new Map(route.verificacao.alertas.filter(a=>a.codigo!=='INDISPONIVEL').map(a=>[a.roleId,a])).values()].map(a=><Button key={a.roleId} secondary title={`Consultar horários de ${a.nome}`} onPress={()=>router.push({pathname:'/roles/[id]',params:{id:a.roleId}})}/>)}
    </View>)}
    {!!routes?.length && <Text style={s.small}>Conferido em {new Date(routes[0].verificacao.verificadoEm).toLocaleString('pt-BR')}. Seu planejamento original foi mantido. A conferência ocorre ao abrir esta tela ou tocar em verificar.</Text>}
  </View>;
}
