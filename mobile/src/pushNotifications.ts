import { requireOptionalNativeModule } from 'expo';
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import { request } from './api';
import { registrationToken } from './pushRegistration';

export type PushStatus = { ativo: boolean; envioConfigurado: boolean; ultimoErro: string | null };
export function notifications() {
  if (Platform.OS !== 'android' || !requireOptionalNativeModule('ExpoPushTokenManager')) return null;
  // Keep existing development APKs usable until the new native module is installed.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return require('expo-notifications') as typeof import('expo-notifications');
}
let operation: Promise<unknown> = Promise.resolve();
export function serializePush<T>(task: () => Promise<T>): Promise<T> {
  const next=operation.catch(()=>{}).then(task); operation=next; return next;
}
export async function registerPush(token: string, ask: boolean, active=()=>true) {
  const n=notifications();
  if(!n) throw new Error('Instale o novo APK com suporte a notificações para ativar os avisos.');
  await n.setNotificationChannelAsync('horarios',{name:'Alterações nos rolês salvos',importance:n.AndroidImportance.DEFAULT});
  let permission=await n.getPermissionsAsync();
  if(!permission.granted && ask) permission=await n.requestPermissionsAsync();
  if(!permission.granted) {
    if(active()) await request('/notificacoes',{method:'DELETE',token});
    throw new Error('Notificações não autorizadas. Você pode permitir os avisos nas configurações do Android.');
  }
  const projectId=Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if(!projectId) throw new Error('Configuração de notificações indisponível neste APK.');
  const expoToken=await registrationToken(
    ()=>n.getDevicePushTokenAsync(),
    devicePushToken=>n.getExpoPushTokenAsync({projectId,devicePushToken}),
  );
  if(active()) return request<PushStatus>('/notificacoes',{method:'PUT',token,body:{expoToken}});
}
