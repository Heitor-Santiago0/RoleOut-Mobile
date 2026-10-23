import { stopTravel } from './travelControl';
import { useCallback, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { ApiError, request } from './api';
import { useAuth } from './AuthContext';
import { Button, Field, Message } from './ui';
import { styles as s } from './theme';
import { suggestionFrequencies, type SuggestionSettings as Settings } from './automaticSuggestions';

export function SuggestionSettings({ token, onChanged }: { token: string; onChanged?: () => void }) {
  const { expire } = useAuth();
  const [value, setValue] = useState<Settings | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [attempt, setAttempt] = useState(0);
  const active = useRef(false), saving = useRef(false);
  useFocusEffect(useCallback(() => {
    active.current = true;
    let current = true;
    request<Settings>('/sugestoes-automaticas', { token }).then(result => { if (current) { setValue(result); setError(''); } }).catch(e => { if (current) setError(e.message); });
    return () => { current = false; active.current = false; };
  // attempt is the explicit reload trigger.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, attempt]));
  async function save(disable = false) {
    if (!value || saving.current) return;
    if (!disable && value.ativo && !value.cidadeBase?.trim()) { setError('Informe sua cidade de referência.'); return; }
    saving.current = true; setBusy(true); setError(''); setNotice('');
    try {
      const result = await request<Settings>('/sugestoes-automaticas', { token, method: 'PUT', body: disable ? { ...value, ativo: false } : value });
      if (!result.ativo || !result.viagens) await stopTravel();
      if (active.current) { setValue(result); setNotice(result.ativo ? 'Preferências salvas. Sugestões aparecerão ao usar o Explorar com localização autorizada.' : 'Sugestões automáticas desativadas.'); }
    } catch (e) { if (active.current) { if (e instanceof ApiError && e.status === 401) void expire(); else setError(e instanceof Error ? e.message : 'Não foi possível salvar.'); } }
    finally { onChanged?.(); saving.current = false; setBusy(false); }
  }
  return <View style={[s.secondary, { padding: 16, borderRadius: 14, gap: 10 }]}>
    <Text style={s.cardTitle}>Sugestões automáticas</Text>
    <Text style={s.text}>Receba uma sugestão de acordo com seus interesses enquanto usa o mapa. Apenas atividades com programação compatível agora e características que combinam com você.</Text>
    <Text style={s.small}>Opcional. Usa a localização autorizada no mapa, sem guardar coordenadas ou histórico de viagens. Para acompanhar viagens em segundo plano, ative também Avisos em viagens no perfil. Os avisos de alterações dos roteiros têm controles separados.</Text>
    <Message text={error} />{!!notice && <Text accessibilityRole="alert" style={s.small}>{notice}</Text>}
    {!value && !error && <Text style={s.small}>Carregando preferências…</Text>}
    {value && <>
      <Button secondary disabled={busy} title={value.ativo ? 'Sugestões selecionadas: ativadas' : 'Sugestões selecionadas: desativadas'} onPress={() => { setValue({ ...value, ativo: !value.ativo }); setNotice('Salve para aplicar esta escolha.'); }} />
      {value.ativo && <>
        <Field label="Minha cidade de referência" value={value.cidadeBase ?? ''} setValue={cidadeBase => setValue({ ...value, cidadeBase })} />
        <Text style={s.small}>Use o nome da cidade, como aparece no mapa. Fora dela, só sugeriremos se você permitir outras cidades.</Text>
        <Button secondary disabled={busy} title={value.viagens ? 'Outras cidades: permitir sugestões' : 'Outras cidades: não sugerir'} onPress={() => setValue({ ...value, viagens: !value.viagens })} />
        <Text style={s.small}>Padrão: até 3 sugestões nas últimas 24 horas. Extras somente com música, culinária e ambiente compatíveis. Intervalo mínimo de 1 hora, sem repetir o mesmo rolê em 24 horas.</Text>
        {suggestionFrequencies.map(option => <Button key={option.hours} secondary disabled={busy} title={`${value.intervaloHoras === option.hours ? '✓ ' : ''}${option.label}`} onPress={() => setValue({ ...value, intervaloHoras: option.hours })} />)}
      </>}
      <Button title="Salvar preferências de sugestões" busy={busy} onPress={() => void save()} />
      <Button title="Desativar sugestões agora" secondary disabled={busy} onPress={() => void save(true)} />
    </>}
    {!!error && <Button title="Recarregar preferências de sugestões" secondary disabled={busy} onPress={() => setAttempt(v => v + 1)} />}
    <Button title="Escolher meus interesses" secondary onPress={() => router.push('/preferences')} />
  </View>;
}
