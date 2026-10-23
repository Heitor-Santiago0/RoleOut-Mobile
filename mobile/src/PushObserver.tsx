import { useEffect } from 'react';
import { AppState } from 'react-native';
import { router } from 'expo-router';
import { useAuth } from './AuthContext';
import { request } from './api';
import { notificationRoute, discoveryNotificationRoute } from './pushPayload';
import { notifications, registerPush, serializePush, type PushStatus } from './pushNotifications';

export function PushObserver() {
  const {session,setMessage}=useAuth();
  useEffect(()=>{
    const n=notifications();if(!n || !session)return;
    let active=true;const seen=new Set<string>();
    n.setNotificationHandler({handleNotification:async()=>({shouldPlaySound:false,shouldSetBadge:false,shouldShowBanner:true,shouldShowList:true})});
    async function open(response: import('expo-notifications').NotificationResponse) {
      const data=response.notification.request.content.data;
      const id=notificationRoute(data), roleId=discoveryNotificationRoute(data);
      const key=response.notification.request.identifier;
      if((id===null && roleId===null) || seen.has(key))return;
      seen.add(key);
      try {
        // Verify ownership with the current account before opening a notification from a previous session.
        await request(roleId!==null ? `/roles/${roleId}` : `/roteiros/${id}`,{token:session!.token});
        if(active) {if(roleId!==null) router.push({pathname:'/roles/[id]',params:{id:roleId}}); else router.push({pathname:'/itineraries/[id]',params:{id:id!}});await n!.clearLastNotificationResponseAsync();}
      } catch {if(active){seen.delete(key);setMessage('Não foi possível abrir o aviso. Entre na conta correta e consulte Meus rolês quando houver conexão.');}}
    }
    const listener=n.addNotificationResponseReceivedListener(response=>{void open(response);});
    async function refresh() {
      try {
        await serializePush(async()=>{
          const status=await request<PushStatus>('/notificacoes',{token:session!.token});
          if(active && status.ativo && status.envioConfigurado) await registerPush(session!.token,false,()=>active);
        });
      } catch { /* Never block app startup or ask permission automatically. Settings allows retry. */ }
    }
    void refresh();
    const last=n.getLastNotificationResponse();if(last)void open(last);
    const app=n.addPushTokenListener(()=>{void refresh();});
    const foreground=AppState.addEventListener('change',state=>{if(state==='active')void refresh();});
    return ()=>{active=false;listener.remove();app.remove();foreground.remove();};
  },[session,setMessage]);
  return null;
}
