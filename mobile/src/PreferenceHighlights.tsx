import { Disclosure } from './Disclosure';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { nearbyHighlights, type Interests } from './recommendations';
import { discoveryTiming } from './discoveryTiming';
import { Button } from './ui';
import { DiscoveryCard } from './DiscoveryCard';
import type { Point } from './NearbyMap.types';
import { colors, styles as s } from './theme';

type Props = { token: string; items: Point[] | null; onSelect: (role: { id: number }) => void };
export function PreferenceHighlights(props: Props) {
  const [attempt, setAttempt] = useState(0);
  return <Highlights key={`${props.token}:${attempt}`} {...props} onRetry={() => setAttempt(value => value + 1)} />;
}
function Highlights({ token, items, onSelect, onRetry }: Props & { onRetry: () => void }) {
  const { expire } = useAuth();
  const [result, setResult] = useState<{ preferences?: Interests; error?: string; pending: boolean }>({ pending: true });
  useFocusEffect(useCallback(() => {
    let active = true;
    request<{ perfil: Interests }>('/preferencias', { token }).then(data => {
      if (active) setResult({ preferences: data.perfil, pending: false });
    }).catch(async error => {
      if (!active) return;
      if (error instanceof ApiError && error.status === 401) { await expire(); return; }
      setResult({ error: 'Não foi possível carregar seus interesses. A busca continua disponível.', pending: false });
    });
    return () => { active = false; };
  }, [token, expire]));
  const [now, setNow] = useState(() => new Date());
  useFocusEffect(useCallback(() => {
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 60000);
    return () => clearInterval(timer);
  }, []));
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const highlights = result.preferences ? nearbyHighlights(items ?? [], result.preferences, today, time) : [];
  const hasChoices = result.preferences?.status === 'CONCLUIDO' && ['musica', 'comida', 'atividades', 'ambientes'].some(key => result.preferences![key as keyof Omit<Interests, 'status'>].length > 0);
  return <View style={{ gap: 12 }}>
    <Text style={s.cardTitle}>Para você</Text>
    {result.pending && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando seus interesses" />}
    {result.error && <><Text style={s.subtitle}>{result.error}</Text><Button title="Recarregar interesses" secondary onPress={onRetry} /></>}
    {result.preferences && <>
      {!hasChoices && <Text style={s.small}>Escolha seus interesses para receber sugestões.</Text>}
      {items === null && <Text style={s.subtitle}>Ative a localização no mapa para receber sugestões próximas.</Text>}
      {items !== null && hasChoices && highlights.length === 0 && <Text style={s.subtitle}>Sem sugestões por aqui agora. Veja os rolês da cidade abaixo.</Text>}
      {highlights.map(({ role, reasons }) => <View key={role.id} style={{ gap: 0 }}>
        <DiscoveryCard name={role.nome} category={role.categoria} subtitle={`${role.distanciaKm.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km · ${discoveryTiming(role, today, time).label}`} onPress={() => onSelect({ id: role.id })} />
        <Disclosure title="Por que este rolê?"><Text style={s.small}>{reasons.join('\n')}</Text><Text style={s.small}>Até 5 km, conforme a programação publicada. Confirme disponibilidade antes de sair.</Text></Disclosure>
      </View>)}
      <Disclosure title="Personalizar sugestões">
        <Text style={s.small}>Priorizamos seus interesses e a programação deste horário, depois opções para mais tarde.</Text>
        <Button title="Editar meus interesses" secondary onPress={() => router.push('/preferences')} />
      </Disclosure>
    </>}
  </View>;
}
