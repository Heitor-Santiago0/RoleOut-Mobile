import { useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { router } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { useSocialResource } from './useSocialResource';
import { Button, Message } from './ui';
import { styles as s } from './theme';
type Venue = { id: number; nome: string; roleId: number | null; seguindo: boolean };
export function VenueFollow({ token, role }: { token: string; role: number }) {
  const path = `/locais-seguidos/${role}`;
  const remote = useSocialResource<Venue>(path, token), { expire } = useAuth();
  const [busy, setBusy] = useState(false), [error, setError] = useState(''); const lock = useRef(false);
  async function toggle() {
    if (!remote.data || lock.current) return;
    lock.current = true; setBusy(true); setError(''); const generation = remote.generation.current;
    try { await request(path, { token, method: remote.data.seguindo ? 'DELETE' : 'PUT' }); if (generation === remote.generation.current) remote.reload(); }
    catch (e) { if (e instanceof ApiError && e.status === 401) void expire(); else if (generation === remote.generation.current) setError(e instanceof Error ? e.message : 'Tente novamente.'); }
    finally { lock.current = false; setBusy(false); }
  }
  return <View><Message text={error} />{remote.data && <Button title={remote.data.seguindo ? 'Seguindo este local' : 'Seguir local'} secondary busy={busy} disabled={remote.loading} onPress={() => void toggle()} />}</View>;
}
export function FollowedVenues({ token }: { token: string }) {
  const remote = useSocialResource<{ itens: Venue[] }>('/locais-seguidos', token);
  return <View style={{ gap: 12 }}><Message text={remote.error} />{remote.loading && <Text style={s.small}>Carregando…</Text>}{remote.data?.itens.map(venue => <Button key={venue.id} title={venue.nome} secondary disabled={!venue.roleId} onPress={() => router.push({ pathname: '/roles/[id]', params: { id: String(venue.roleId) } })} />)}{remote.data?.itens.length === 0 && <Text style={s.small}>Siga um local na página dele para encontrá-lo aqui.</Text>}</View>;
}
