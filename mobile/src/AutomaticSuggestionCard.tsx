import { Disclosure } from './Disclosure';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { request } from './api';
import { Button } from './ui';
import { styles as s } from './theme';
import { freshContext, type DiscoveryContext, type SuggestionSettings, type AutomaticSuggestion } from './automaticSuggestions';

export function AutomaticSuggestionCard({ token, context, onSelect }: { token: string; context: DiscoveryContext | null; onSelect: (role: { id: number }) => void }) {
  const latest = useRef(context);
  useEffect(() => { latest.current = context; }, [context]);
  const [suggestion, setSuggestion] = useState<AutomaticSuggestion | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const hasContext = context !== null;
  useFocusEffect(useCallback(() => {
    if (!hasContext) return;
    let active = true, pending = false;
    async function check() {
      if (!active || pending || AppState.currentState !== 'active') return;
      const position = latest.current;
      if (!freshContext(position, Date.now())) return;
      pending = true;
      try {
        const settings = await request<SuggestionSettings>('/sugestoes-automaticas', { token });
        if (!active || !settings.ativo || AppState.currentState !== 'active') { if (active) { setSuggestion(null); setError(''); } return; }
        const current = latest.current;
        if (!freshContext(current, Date.now()) || current.cidade !== position.cidade) return;
        const result = await request<{ sugestao: AutomaticSuggestion | null }>('/sugestoes-automaticas', {
          token, method: 'POST', body: { latitude: current.latitude, longitude: current.longitude, cidade: current.cidade, fuso: Intl.DateTimeFormat().resolvedOptions().timeZone },
        });
        if (active && AppState.currentState === 'active' && freshContext(latest.current, Date.now()) && latest.current.cidade === current.cidade) { setSuggestion(result.sugestao); setError(''); }
      } catch { if (active) setError('Não foi possível conferir as sugestões automáticas. A busca continua disponível.'); }
      finally { pending = false; }
    }
    void check();
    const timer = setInterval(() => void check(), 60000);
    return () => { active = false; clearInterval(timer); setSuggestion(null); };
  // attempt is the explicit retry trigger.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, hasContext, attempt]));
  if (!hasContext) return null;
  if (error) return <View style={{ gap: 8 }}><Text style={s.small}>{error}</Text><Button secondary title="Repetir busca de sugestão" onPress={() => setAttempt(v => v + 1)} /></View>;
  if (!suggestion) return null;
  return <View style={[s.secondary, { padding: 16, borderRadius: 14, gap: 10 }]}>
    <Text accessibilityRole="header" style={s.cardTitle}>{suggestion.outraCidade ? `Uma sugestão em ${suggestion.cidade}` : 'Uma sugestão para agora'}</Text>
    <Text style={s.text}>{suggestion.nome}</Text>
    <Disclosure title="Por que esta sugestão?"><Text style={s.small}>{suggestion.motivos.join(' · ')} · {suggestion.distanciaKm.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km em linha reta</Text>
    <Text style={s.small}>Conforme informações publicadas pelo responsável. Confira disponibilidade antes de sair.</Text></Disclosure>
    <Button title="Ver local" onPress={() => { setSuggestion(null); onSelect({ id: suggestion.roleId }); }} />
    <Button secondary title="Dispensar sugestão" onPress={() => setSuggestion(null)} />
    <Disclosure title="Opções de sugestões"><Button secondary title="Controlar sugestões" onPress={() => router.push('/account')} /></Disclosure>
  </View>;
}
