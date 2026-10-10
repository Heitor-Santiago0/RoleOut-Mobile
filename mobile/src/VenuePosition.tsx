import { useEffect, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import * as Location from 'expo-location';
import { Button, Field, Message } from './ui';
import { styles as s } from './theme';

export function VenuePosition({ latitude, longitude, onChange, disabled }: { latitude: string; longitude: string; onChange: (latitude: string, longitude: string) => void; disabled: boolean }) {
  const active = useRef(true); const running = useRef(false);
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [candidate, setCandidate] = useState<Location.LocationObject | null>(null);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  async function locate() {
    if (running.current) return; running.current = true; setBusy(true); setError(''); setCandidate(null);
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!active.current) return;
      if (!permission.granted) { setError('Localização não autorizada. Você pode informar as coordenadas manualmente.'); return; }
      if (!await Location.hasServicesEnabledAsync()) throw new Error('Ative a localização do aparelho.');
      const position = await Promise.race([
        Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High }),
        new Promise<never>((_, reject) => { timer = setTimeout(() => reject(new Error('A localização demorou. Tente novamente em uma área aberta.')), 20000); }),
      ]);
      if (!active.current) return;
      if (!Number.isFinite(position.coords.latitude) || !Number.isFinite(position.coords.longitude)) throw new Error('Posição inválida. Tente novamente.');
      if (position.coords.accuracy === null || position.coords.accuracy > 100) throw new Error('A posição está imprecisa. Tente novamente perto da entrada do local ou informe as coordenadas manualmente.');
      setCandidate(position);
    } catch (e) { if (active.current) setError(e instanceof Error ? e.message : 'Não foi possível obter a posição.'); }
    finally { if (timer) clearTimeout(timer); running.current = false; if (active.current) setBusy(false); }
  }
  return <View style={{ gap: 10 }}>
    <Text style={s.cardTitle}>Posição do local no mapa (opcional)</Text>
    <Text style={s.small}>Informe a posição da entrada do estabelecimento. Use sua posição atual somente se estiver lá. Após aprovação, essa posição poderá aparecer no mapa público.</Text>
    <Field label="Latitude do local" placeholder="Ex.: -19,750000" value={latitude} setValue={value => onChange(value, longitude)} />
    <Field label="Longitude do local" placeholder="Ex.: -47,930000" value={longitude} setValue={value => onChange(latitude, value)} />
    <Button title="Obter minha posição para este local" secondary busy={busy} disabled={disabled} onPress={() => { void locate(); }} />
    <Message text={error} />
    {candidate && <>
      <Text style={s.text}>Posição encontrada: {candidate.coords.latitude.toFixed(6)}, {candidate.coords.longitude.toFixed(6)}. Precisão aproximada: {Math.round(candidate.coords.accuracy ?? 0)} m.</Text>
      <Button title="Estou no local: usar esta posição" secondary disabled={disabled || busy} onPress={() => { onChange(candidate.coords.latitude.toFixed(6), candidate.coords.longitude.toFixed(6)); setCandidate(null); }} />
    </>}
    {(latitude || longitude) && <Button title="Remover posição do cadastro" secondary disabled={disabled || busy} onPress={() => { onChange('', ''); setCandidate(null); setError(''); }} />}
    <Text style={s.small}>Sem posição, os rolês podem aparecer na busca por cidade, mas não no mapa de proximidade. As coordenadas só serão enviadas ao salvar o local.</Text>
  </View>;
}
