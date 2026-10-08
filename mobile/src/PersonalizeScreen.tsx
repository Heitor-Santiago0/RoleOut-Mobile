import { useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { router } from 'expo-router';
import { request, type Role } from './api';
import { useAuth } from './AuthContext';
import { demoRoles } from './demo';
import { Button, DemoBanner, Field, Message, price } from './ui';
import { styles as s } from './theme';

export function PersonalizeScreen() {
  const { demo, session } = useAuth();
  const [city, setCity] = useState(''); const [budget, setBudget] = useState('');
  const [category, setCategory] = useState(''); const [error, setError] = useState('');
  const [busy, setBusy] = useState(false); const [items, setItems] = useState<Role[] | null>(null);
  const generation = useRef(0);
  function change() { generation.current++; setItems(null); setError(''); setBusy(false); }
  async function search() {
    const amount = Number(budget.replace(',', '.'));
    if (!city.trim() || !category || !budget.trim() || !Number.isFinite(amount) || amount < 0) {
      setError('Informe cidade, categoria e orçamento válido (zero para opções gratuitas).'); return;
    }
    const version = ++generation.current; setBusy(true); setError(''); setItems(null);
    try {
      const data = demo ? { itens: demoRoles.filter(r => r.cidade.toLowerCase() === city.trim().toLowerCase() && r.categoria === category && r.valor <= amount).sort((a, b) => a.valor - b.valor) }
        : await request<{ itens: Role[] }>(`/sugestoes?${new URLSearchParams({ cidade: city.trim(), categoria: category, valorMaximo: String(amount), limite: '100' })}`, { token: session?.token });
      if (version === generation.current) setItems(data.itens);
    } catch (e) { if (version === generation.current) setError(e instanceof Error ? e.message : 'Não foi possível buscar.'); }
    finally { if (version === generation.current) setBusy(false); }
  }
  return <ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    {demo && <DemoBanner />}<Text style={s.title}>Criar meu rolê</Text>
    <Text style={s.subtitle}>Comece do zero: escolha suas preferências e encontre opções que cabem no seu orçamento.</Text>
    <Field label="Cidade" value={city} setValue={value => { change(); setCity(value); }} placeholder="Ex.: Uberaba" />
    <Text style={s.label}>Categoria</Text><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
      {['Música', 'Festa', 'Gastronomia', 'Cultura', 'Esporte', 'Natureza', 'Cinema', 'Outros'].map(value => <Pressable key={value} accessibilityRole="button" accessibilityState={{ selected: category === value }} style={[s.chip, category === value && s.selected]} onPress={() => { change(); setCategory(value); }}><Text style={[s.chipText, category === value && s.selectedText]}>{value}</Text></Pressable>)}
    </View>
    <Field label="Orçamento máximo (R$)" value={budget} setValue={value => { change(); setBudget(value); }} keyboardType="decimal-pad" placeholder="Ex.: 50,00" />
    <Message text={error} /><Button title="Encontrar meu rolê" onPress={search} busy={busy} />
    {items && <><Text style={s.label}>Opções para o seu rolê</Text><Text style={s.small}>Até 100 opções, do menor preço ao maior. O valor é por opção.</Text>
      {items.length === 0 && <Text style={s.subtitle}>Nenhuma opção encontrada. Experimente outra categoria ou orçamento.</Text>}
      {items.map(role => <Pressable key={role.id} accessibilityRole="button" accessibilityLabel={`Ver ${role.nome}`} style={[s.card, { flex: undefined, padding: 16, gap: 8 }]} onPress={() => router.push(`/roles/${role.id}`)}><Text style={s.cardTitle}>{role.nome}</Text><Text style={s.subtitle}>{role.cidade} • {price(role.valor)}</Text></Pressable>)}
    </>}
  </ScrollView>;
}
