import { useEffect, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { request, type Role } from './api';
import { Button, DemoBanner, Message, price } from './ui';
import { colors, styles as s } from './theme';
export function DetailScreen({ selected, demo, token, onBack }: { selected: Role | { id: number }; demo: boolean; token?: string; onBack: () => void }) {
  const [role, setRole] = useState<Role | null>(demo && 'nome' in selected ? selected : null); const [error, setError] = useState(''); const [retry, setRetry] = useState(0); const [busy, setBusy] = useState(!demo);
  useEffect(() => {
    if (demo) return;
    let active = true;
    request<Role>(`/roles/${selected.id}`, { token }).then(value => { if (active) setRole(value); }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Não foi possível carregar.'); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [selected.id, demo, token, retry]);
  return <ScrollView contentContainerStyle={s.content}><Button title="← Voltar" secondary onPress={onBack} />{demo && <DemoBanner />}{busy && <ActivityIndicator color={colors.blue} />}<Message text={error} />{!!error && <Button title="Tentar novamente" onPress={() => { setBusy(true); setError(''); setRetry(v => v + 1); }} />}
    {role && !busy && !error && <><View style={[s.tile, { height: 170, borderRadius: 14 }]}><Text style={s.tileText}>{role.categoria ?? 'Rolê'}</Text></View><Text style={s.title}>{role.nome}</Text><View style={[s.chip, s.selected, { alignSelf: 'flex-start' }]}><Text style={s.selectedText}>{role.categoria ?? 'Sem categoria'}</Text></View><Text style={s.text}>{price(role.valor)}</Text><Text style={s.text}>{role.descricao ?? 'Descrição não informada.'}</Text><Text style={s.label}>Onde</Text><Text style={s.subtitle}>{role.local ?? 'Local não informado'} • {role.cidade}</Text><Text style={s.label}>Quando</Text><Text style={s.subtitle}>{role.data ? role.data.split('-').reverse().join('/') : 'Data não informada'}</Text></>}
  </ScrollView>;
}
