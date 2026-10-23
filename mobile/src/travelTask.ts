import { AppState } from 'react-native';
import * as Location from 'expo-location';
import { ApiError, request } from './api';
import { readToken } from './session';
import { notifications } from './pushNotifications';
import { claimTravelCheck, serializeTravel, stopTravel, travelManager, travelSession, travelTaskName } from './travelControl';
import { evaluateTravel, type TravelFix } from './travelSuggestions';
import type { AutomaticSuggestion, SuggestionSettings } from './automaticSuggestions';

const manager = travelManager();
if (manager) notifications()?.setNotificationHandler({ handleNotification: async () => ({ shouldPlaySound: false, shouldSetBadge: false, shouldShowBanner: true, shouldShowList: true }) });
if (manager && !manager.isTaskDefined(travelTaskName)) manager.defineTask<{ locations: Location.LocationObject[] }>(travelTaskName, async ({ data, error }) => {
  if (error || !data || AppState.currentState === 'active') return;
  await serializeTravel(async () => {
    const n = notifications();
    const token = await readToken();
    if (!token || !n || await travelSession() !== token) { await stopTravel(); return; }
    if (!await claimTravelCheck(Date.now())) return;
    try {
      const fix = data.locations.reduce<TravelFix | undefined>((latest, item) => !latest || item.timestamp > latest.timestamp ? item : latest, undefined);
      await evaluateTravel(fix, Date.now(), {
        allowed: async () => await travelSession() === token && await readToken() === token,
        permissions: async () => (await Location.getBackgroundPermissionsAsync()).granted && (await n.getPermissionsAsync()).granted,
        stop: stopTravel,
        settings: () => request<SuggestionSettings>('/sugestoes-automaticas', { token }),
        city: async point => {
          // At most once per 15 minutes, only with explicit travel consent; never persist the address.
          const addresses = await Location.reverseGeocodeAsync(point.coords);
          return addresses[0]?.city ?? addresses[0]?.subregion ?? null;
        },
        evaluate: async (point, cidade) => (await request<{ sugestao: AutomaticSuggestion | null }>('/sugestoes-automaticas', { token, method: 'POST', body: { latitude: point.coords.latitude, longitude: point.coords.longitude, cidade, fuso: Intl.DateTimeFormat().resolvedOptions().timeZone } })).sugestao,
        notify: async suggestion => {
          await n.scheduleNotificationAsync({ identifier: 'roleout-travel', content: { title: `Um rolê em ${suggestion.cidade}`, body: `${suggestion.nome}. Confira os detalhes antes de sair.`, data: { tipo: 'descoberta', roleId: suggestion.roleId }, sound: false }, trigger: { channelId: 'descobertas' } });
        },
      });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) await stopTravel();
      // Offline: no stale recommendation, no immediate retry and no coordinates logged or stored.
    }
  }).catch(() => {});
});
