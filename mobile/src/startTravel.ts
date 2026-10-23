import { AppState } from 'react-native';
import * as Location from 'expo-location';
import { request } from './api';
import { readToken } from './session';
import { notifications } from './pushNotifications';
import { consentTravel, stopTravel, travelManager, travelSession, travelTaskName } from './travelControl';
import type { SuggestionSettings } from './automaticSuggestions';
export async function startTravel(token: string, active = () => true) {
  if (!active()) return false;
  if (AppState.currentState !== 'active') throw new Error('Volte ao aplicativo para ativar os avisos.');
  const manager = travelManager(), n = notifications();
  if (!manager || !n || !await manager.isAvailableAsync()) throw new Error('Instale o novo APK Android com suporte a avisos em viagens.');
  const settings = await request<SuggestionSettings>('/sugestoes-automaticas', { token });
  if (!settings.ativo || !settings.viagens) throw new Error('Primeiro salve as sugestões automáticas ativadas e permita outras cidades.');
  if (!active()) return false;
  await n.setNotificationChannelAsync('descobertas', { name: 'Sugestões em viagens', importance: n.AndroidImportance.DEFAULT });
  if (!(await n.requestPermissionsAsync()).granted) throw new Error('Autorize as notificações nas configurações do Android.');
  if (!active()) return false;
  if (!(await Location.requestForegroundPermissionsAsync()).granted) throw new Error('Autorize a localização para continuar.');
  if (!active()) return false;
  if (!(await Location.requestBackgroundPermissionsAsync()).granted) throw new Error('Para viagens, selecione Permitir o tempo todo nas permissões de localização do Android.');
  if (!active()) return false;
  if (await readToken() !== token) throw new Error('Entre novamente antes de ativar os avisos.');
  await consentTravel(token);
  try {
    await Location.startLocationUpdatesAsync(travelTaskName, {
      accuracy: Location.Accuracy.Balanced, distanceInterval: 1000, timeInterval: 15 * 60000,
      deferredUpdatesInterval: 15 * 60000, deferredUpdatesDistance: 1000,
      foregroundService: { notificationTitle: 'RolêOut: sugestões em viagens', notificationBody: 'Localização ativa para descobrir rolês. Desative em Perfil → Configurações → Avisos em viagens.', killServiceOnDestroy: true },
    });
    if (!active() || await readToken() !== token || await travelSession() !== token) { await stopTravel(); return false; }
    if (!await Location.hasStartedLocationUpdatesAsync(travelTaskName)) throw new Error('Serviço não iniciado.');
    return true;

  } catch { await stopTravel(); throw new Error('Não foi possível iniciar. Confira as permissões e instale o novo APK com localização em segundo plano.'); }
}
