import { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { request } from './api';
import { Button } from './ui';
import { characteristicSections, type Characteristics } from './roleCharacteristics';
import { styles as s } from './theme';

export function CharacteristicsEditor({ token, value, onChange, disabled }: { token: string; value: Characteristics; onChange: (value: Characteristics) => void; disabled: boolean }) {
  const [options, setOptions] = useState<Characteristics | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    request<{ opcoes: Characteristics }>('/preferencias', { token }).then(data => { if (active) setOptions(data.opcoes); }).catch(() => { if (active) setError(true); });
    return () => { active = false; };
  }, [token, attempt]);
  return <View style={{ gap: 12 }}>
    <Text style={s.cardTitle}>Características do rolê</Text>
    <Text style={s.small}>Marque apenas o que é oferecido nesta atividade. As informações ajudam nas recomendações e serão analisadas antes de aparecer para o público. Deixe vazio o que não souber.</Text>
    {!options && !error && <Text style={s.small}>Carregando opções…</Text>}
    {error && <><Text style={s.small}>Não foi possível carregar as opções. As características já preenchidas serão preservadas.</Text><Button title="Recarregar opções" secondary disabled={disabled} onPress={() => { setError(false); setAttempt(n => n + 1); }} /></>}
    {options && characteristicSections.map(({ key, title }) => <View key={key} style={{ gap: 8 }}>
      <Text style={s.label}>{title}</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {options[key].map(option => {
          const checked = value[key].includes(option);
          return <Pressable key={option} accessibilityRole="checkbox" accessibilityLabel={title + ': ' + option} accessibilityState={{ checked, disabled }} disabled={disabled}
            style={[s.chip, checked && s.selected]} onPress={() => onChange({ ...value, [key]: checked ? value[key].filter(v => v !== option) : [...value[key], option] })}>
            <Text style={[s.chipText, checked && s.selectedText]}>{checked ? '✓ ' : ''}{option}</Text>
          </Pressable>;
        })}
      </View>
    </View>)}
  </View>;
}
