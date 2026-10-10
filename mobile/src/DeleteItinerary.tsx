import { useCallback, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { Button, Message } from './ui';
import { styles as s } from './theme';

export function DeleteItinerary({ id, name, token }: { id: number; name: string; token: string }) {
  const { expire } = useAuth();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const generation = useRef(0);
  const deleting = useRef(false);
  useFocusEffect(useCallback(() => {
    setConfirming(false); setError('');
    return () => { generation.current++; };
  }, []));
  async function remove() {
    if (deleting.current) return;
    const version = generation.current;
    deleting.current = true; setBusy(true); setError('');
    try {
      await request(`/roteiros/${id}`, { method: 'DELETE', token });
      if (version === generation.current) router.replace('/itineraries');
    } catch (e) {
      if (version !== generation.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else if (e instanceof ApiError && e.status === 404) router.replace('/itineraries');
      else setError(e instanceof Error ? e.message : 'Não foi possível excluir o roteiro.');
    } finally { deleting.current = false; setBusy(false); }
  }
  return <View>{confirming ? <>
    <Text style={s.cardTitle}>Excluir “{name}”?</Text>
    <Text style={s.text}>O roteiro e suas paradas salvas serão removidos da sua conta. Essa ação não pode ser desfeita.</Text>
    <Message text={error} />
    <Button title="Cancelar exclusão" secondary disabled={busy} onPress={() => { setConfirming(false); setError(''); }} />
    <Button title="Confirmar exclusão" busy={busy} onPress={remove} />
  </> : <Button title="Excluir roteiro" secondary onPress={() => setConfirming(true)} />}</View>;
}
