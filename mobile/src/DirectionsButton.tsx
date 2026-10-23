import { useState } from 'react';
import { Text } from 'react-native';
import * as Linking from 'expo-linking';
import { request, type Role } from './api';
import { useAuth } from './AuthContext';
import { directionsUrl } from './directions';
import { Button, Message } from './ui';
import { styles as s } from './theme';

export function DirectionsButton({ role, demo = false, title = 'Ver como chegar lá' }: { role: Pick<Role, 'id' | 'local' | 'cidade' | 'latitude' | 'longitude'>; demo?: boolean; title?: string }) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const { session } = useAuth();
  if (demo) return <Text style={s.small}>Trajeto indisponível para este local.</Text>;
  return <>
    <Button title={title} secondary busy={busy} onPress={() => {
      setError(''); setBusy(true);
      void (async () => {
        try {
          const current = await request<Role>(`/roles/${role.id}`, { token: session?.token });
          if (current.cancelado) throw new Error('Este evento foi cancelado. Consulte os detalhes e revise seu roteiro.');
          const url = directionsUrl(current);
          if (!url) throw new Error('Este local ainda não possui um destino cadastrado.');
          await Linking.openURL(url);
        } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível abrir o Google Maps. Tente novamente.'); }
        finally { setBusy(false); }
      })();
    }} />
    <Text style={s.small}>Abre no Google Maps usando o ponto cadastrado. Se não houver coordenadas, busca pelo local e cidade; confira o destino antes de sair.</Text>
    <Message text={error} />
  </>;
}
