import { useCallback, useState } from 'react';
import { Text } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { request, type SavedItinerary } from './api';
import { useAuth } from './AuthContext';
import { DirectionsButton } from './DirectionsButton';
import { Button, Message } from './ui';
import { styles as s } from './theme';

export function SavedRouteButton({ id }: { id: number }) {
  const [attempt, setAttempt] = useState(0);
  const { session } = useAuth();
  return <SavedRoute key={`${session?.token}:${id}:${attempt}`} id={id} onRetry={() => setAttempt(value => value + 1)} />;
}
function SavedRoute({ id, onRetry }: { id: number; onRetry: () => void }) {
  const { session } = useAuth();
  const token = session?.token;
  const [data, setData] = useState<SavedItinerary | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);
  useFocusEffect(useCallback(() => {
    let active = true;
    setBusy(true); setError('');
    if (token) void request<SavedItinerary>(`/roteiros/${id}`, { token })
      .then(value => { if (active) setData(value); })
      .catch(() => { if (active) setError('Não foi possível consultar a próxima parada.'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [id, token]));
  if (!token) return null;
  if (busy) return <Text style={s.small}>Consultando progresso…</Text>;
  if (error) return <><Message text={error} /><Button title="Tentar novamente" onPress={onRetry} /></>;
  if (!data || data.roteiro.id !== id) return null;
  const index = data.paradas.findIndex(stop => !stop.concluida);
  if (index < 0) return <Text style={s.label}>Rolê concluído!</Text>;
  const stop = data.paradas[index];
  if (stop.cancelado) return <Message text="A próxima parada foi cancelada. Consulte o roteiro antes de sair." />;
  return <DirectionsButton role={{ id: stop.roleId, local: stop.local, cidade: data.roteiro.cidade }} title={data.paradas.length === 1 ? 'Ver como chegar lá' : index === 0 ? 'Ver rota da primeira parada' : 'Ver rota da próxima parada'} />;
}
