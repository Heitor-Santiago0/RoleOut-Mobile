import { useEffect } from 'react';
import { AppState } from 'react-native';
import * as Location from 'expo-location';
import { useAuth } from './AuthContext';
import { ApiError, request } from './api';
import type { SuggestionSettings } from './automaticSuggestions';
import { notifications } from './pushNotifications';
import { stopTravel, travelSession } from './travelControl';

// Never asks permission or silently restarts tracking on app launch.
export function TravelObserver() {
  const { session } = useAuth();
  useEffect(() => {
    let active = true;
    async function reconcile() {
      try {
        const consent = await travelSession();
        if (!consent || !active) return;
        if (!session || consent !== session.token) { await stopTravel(); return; }
        const n = notifications();
        if (!n || !(await n.getPermissionsAsync()).granted || !(await Location.getBackgroundPermissionsAsync()).granted) { await stopTravel(); return; }
        const settings = await request<SuggestionSettings>('/sugestoes-automaticas', { token: session.token });
        if (active && await travelSession() === consent && (!settings.ativo || !settings.viagens)) await stopTravel();
      } catch (e) { if (active && e instanceof ApiError && e.status === 401) await stopTravel().catch(() => {}); }
    }
    void reconcile();
    const listener = AppState.addEventListener('change', state => { if (state === 'active') void reconcile(); });
    return () => { active = false; listener.remove(); };
  }, [session]);
  return null;
}
