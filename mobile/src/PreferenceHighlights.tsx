import { useCallback, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { ApiError, request, type Role } from './api';
import { useAuth } from './AuthContext';
import { preferenceHighlights, type Interests } from './recommendations';
import { Button, price } from './ui';
import { colors, styles as s } from './theme';

type Props = { token: string; items: Role[]; onSelect: (role: Role) => void };
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
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const highlights = result.preferences ? preferenceHighlights(items, result.preferences, today) : [];
  const hasChoices = result.preferences?.status === 'CONCLUIDO' && ['musica', 'comida', 'atividades', 'ambientes'].some(key => result.preferences![key as keyof Omit<Interests, 'status'>].length > 0);
  return <View style={{ gap: 12 }}>
    <Text style={s.cardTitle}>Seus interesses nesta busca</Text>
    {result.pending && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando seus interesses" />}
    {result.error && <><Text style={s.subtitle}>{result.error}</Text><Button title="Recarregar interesses" secondary onPress={onRetry} /></>}
    {result.preferences && <>
      <Text style={s.small}>{hasChoices ? 'Até três destaques por categoria entre os resultados carregados. Não indicam disponibilidade nem compatibilidade exata.' : 'Escolha interesses para destacar categorias que combinam com você.'}</Text>
      {hasChoices && highlights.length === 0 && <Text style={s.subtitle}>Nenhuma afinidade identificada nos resultados carregados. Você pode explorar todos os rolês abaixo.</Text>}
      {highlights.map(({ role, reasons }) => <View key={role.id} style={[s.secondary, { borderRadius: 14, padding: 16, gap: 8 }]}>
        <Text style={s.cardTitle}>{role.nome}</Text><Text style={s.small}>{role.cidade} • {role.categoria} • {price(role.valor)}</Text>
        <Text style={s.subtitle}>{reasons[0]}</Text>
        <Button title={`Conhecer ${role.nome}`} secondary onPress={() => onSelect(role)} />
      </View>)}
      <Button title="Editar meus interesses" secondary onPress={() => router.push('/preferences')} />
    </>}
  </View>;
}
