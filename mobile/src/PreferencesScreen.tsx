import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Text, View } from 'react-native';
import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useAuth } from './AuthContext';
import { ApiError, request } from './api';
import { Button, Message } from './ui';
import { colors, styles as s } from './theme';

const sections = [
  { key: 'musica', title: 'Que música você curte?' },
  { key: 'comida', title: 'O que você gosta de comer?' },
  { key: 'atividades', title: 'O que gosta de fazer?' },
  { key: 'ambientes', title: 'Qual é o seu clima?', options: ['Tranquilo', 'Animado', 'Ao ar livre', 'Familiar', 'Romântico', 'Cultural'] },
  { key: 'ambientes', title: 'Quem costuma ir com você?', options: ['Com amigos', 'Com crianças', 'Pet friendly', 'Conhecer pessoas'] },
  { key: 'ambientes', title: 'Que experiência procura?', options: ['Contato com a natureza', 'Contemplativo', 'Aventura', 'Ao pôr do sol'] },
] as const;
type Category = typeof sections[number]['key'];
type Selection = Record<Category, string[]>;
type Preferences = { perfil: Selection & { status: string }; opcoes: Selection };

export function PreferencesScreen() {
  const { session } = useAuth();
  if (!session) return <Redirect href="/auth" />;
  return <Editor key={session.token} token={session.token} />;
}
function Editor({ token }: { token: string }) {
  const { expire } = useAuth();
  const { onboarding } = useLocalSearchParams<{ onboarding?: string }>();
  const initial = onboarding === '1';
  const [data, setData] = useState<Preferences | null>(null);
  const [selected, setSelected] = useState<Selection>({ musica: [], comida: [], atividades: [], ambientes: [] });
  const [step, setStep] = useState(0); const [busy, setBusy] = useState(false);
  const [error, setError] = useState(''); const [attempt, setAttempt] = useState(0);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    let active = true;
    request<Preferences>('/preferencias', { token }).then(value => {
      if (!active) return;
      if (initial && value.perfil.status !== 'PENDENTE') { router.replace('/explore'); return; }
      setData(value); setSelected({ musica: value.perfil.musica, comida: value.perfil.comida, atividades: value.perfil.atividades, ambientes: value.perfil.ambientes });
    }).catch(async e => {
      if (!active) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível carregar suas preferências.');
    });
    return () => { active = false; };
  }, [token, initial, attempt, expire]);
  function finish() { router.replace(initial ? '/explore' : '/account'); }
  async function save(skip: boolean) {
    setBusy(true); setError('');
    try {
      await request('/preferencias', { method: 'POST', token, body: { ...selected, pular: skip } });
      if (mounted.current) finish();
    } catch (e) {
      if (!mounted.current) return;
      if (e instanceof ApiError && e.status === 401) await expire();
      else setError(e instanceof Error ? e.message : 'Não foi possível salvar.');
    } finally { if (mounted.current) setBusy(false); }
  }
  const section = sections[step];
  return <ScrollView contentContainerStyle={[s.content, { maxWidth: 640, width: '100%', alignSelf: 'center' }]}>
    <Text style={s.title}>Seu jeito de curtir</Text>
    <Text style={s.subtitle}>Personalize suas descobertas. Você pode mudar depois.</Text>
    <Message text={error} />
    {!data && !error && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando preferências" />}
    {!data && !!error && <Button title="Tentar novamente" onPress={() => { setError(''); setAttempt(value => value + 1); }} />}
    {data && <>
      <Text style={s.small}>Etapa {step + 1} de {sections.length}</Text>
      <Text style={s.cardTitle}>{section.title}</Text>
      <Text style={s.subtitle}>Escolha quantas quiser.</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
        {data.opcoes[section.key].filter(option => !('options' in section) || (section.options as readonly string[]).includes(option)).map(option => {
          const checked = selected[section.key].includes(option);
          return <Pressable key={option} accessibilityRole="checkbox" accessibilityLabel={option}
            accessibilityState={{ checked, disabled: busy }} disabled={busy}
            style={[s.chip, checked && s.selected]}
            onPress={() => setSelected(previous => ({ ...previous, [section.key]: checked ? previous[section.key].filter(item => item !== option) : [...previous[section.key], option] }))}>
            <Text style={[s.chipText, checked && s.selectedText]}>{checked ? '✓ ' : ''}{option}</Text>
          </Pressable>;
        })}
      </View>
      {step < sections.length - 1
        ? <Button title="Continuar" disabled={busy} onPress={() => setStep(value => value + 1)} />
        : <Button title="Salvar preferências" busy={busy} onPress={() => { void save(false); }} />}
      {step > 0 && <Button title="Voltar uma etapa" secondary disabled={busy} onPress={() => setStep(value => value - 1)} />}
      {initial && <Button title="Pular por enquanto" secondary disabled={busy} onPress={() => { void save(true); }} />}
    </>}
    <Button title={initial ? 'Continuar sem salvar' : 'Cancelar'} secondary disabled={busy} onPress={finish} />
  </ScrollView>;
}
