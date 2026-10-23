import { useEffect, useRef, useState } from 'react';
import { Platform, Text, View } from 'react-native';
import * as Location from 'expo-location';
import { useAuth } from './AuthContext';
import { useRoutingPlan } from './RoutingPlanContext';
import { request, type Role } from './api';
import { isoDate } from './itinerary';
import { smartOrder } from './smartOrder';
import { Button } from './ui';
import { styles as s } from './theme';

export function SmartOrderControl({ start, date, disabled, onBusyChange }: { start: string; date: string; disabled: boolean; onBusyChange?: (busy: boolean) => void }) {
  const { stops, setStops, setSaved } = useRoutingPlan();
  const { session, demo } = useAuth();
  const last = useRef('');
  const [retry, setRetry] = useState(0); const [busy, setBusy] = useState(false); const [notice, setNotice] = useState('');
  useEffect(() => { onBusyChange?.(busy); }, [busy, onBusyChange]);
  useEffect(() => {
    if (disabled || stops.length < 2 || !/^([01]\d|2[0-3]):[0-5]\d$/.test(start)) return;
    const key = JSON.stringify([stops.map(stop => [stop.role.id, stop.horario, stop.minutes]).sort((a, b) => Number(a[0]) - Number(b[0])), start, date, retry]);
    if (last.current === key) return;
    last.current = key;
    let active = true;
    const snapshot = JSON.stringify(stops);
    setBusy(true); setNotice('Organizando as paradas…');
    (async () => {
      const enriched = await Promise.all(stops.map(async stop => ({ ...stop, role: demo ? stop.role : await request<Role>(`/roles/${stop.role.id}`, { token: session?.token }) })));
      let origin;
      if (Platform.OS !== 'web' && (await Location.getForegroundPermissionsAsync()).granted) {
        origin = (await Location.getLastKnownPositionAsync({ maxAge: 120000, requiredAccuracy: 100 }).catch(() => null))?.coords;
      }
      const ordered = smartOrder(enriched, start, origin, isoDate(date));
      if (!active) return;
      setStops(previous => JSON.stringify(previous) === snapshot ? ordered : previous);
      setSaved(null);
      setNotice(origin ? 'Ordem sugerida a partir da sua posição.' : 'Sem posição recente: a primeira parada foi usada como ponto de partida.');
    })().catch(e => { if (active) setNotice(e instanceof Error ? e.message : 'Não foi possível organizar. Sua ordem foi mantida.'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; setBusy(false); };
  }, [stops, start, date, disabled, retry, session?.token, demo, setStops, setSaved]);
  if (stops.length < 2) return null;
  return <View style={{ gap: 8 }}><Button title="Reorganizar por proximidade e horários" busy={busy} disabled={disabled} onPress={() => { last.current = ''; setRetry(value => value + 1); }} />
    {!!notice && <Text style={s.small}>{notice}</Text>}
    <Text style={s.small}>Usa distância em linha reta e reserva 30 minutos antes de compromissos. Não calcula trânsito nem tempo real de deslocamento. Revise a ordem antes de salvar.</Text>
  </View>;
}
