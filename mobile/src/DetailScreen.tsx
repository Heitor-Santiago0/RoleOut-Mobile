import { describeCharacteristics } from './roleCharacteristics';
import { describeHours } from './openingHours';
import { RoleIllustration } from './RoleIllustration';
import { useEffect, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, View } from 'react-native';
import { request, type Role } from './api';
import { Button, DemoBanner, Message, price } from './ui';
import { colors, styles as s } from './theme';
import { router } from 'expo-router';
import { useRoutingPlan } from './RoutingPlanContext';
import { additionProblem, appendStop } from './itinerary';
export function DetailScreen({ selected, demo, token }: { selected: Role | { id: number }; demo: boolean; token?: string }) {
  const { plan, setPlan, stops, setStops, setSaved, saved, startNew } = useRoutingPlan();
  const included = !saved && stops.some(stop => stop.role.id === selected.id);
  const [selectionError, setSelectionError] = useState('');
  const [role, setRole] = useState<Role | null>(demo && 'nome' in selected ? selected : null); const [error, setError] = useState(''); const [retry, setRetry] = useState(0); const [busy, setBusy] = useState(!demo);
  useEffect(() => {
    if (demo) return;
    let active = true;
    request<Role>(`/roles/${selected.id}`, { token }).then(value => { if (active) setRole(value); }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Não foi possível carregar.'); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [selected.id, demo, token, retry]);
  return <ScrollView contentContainerStyle={s.content}>{demo && <DemoBanner />}{busy && <ActivityIndicator color={colors.blue} />}<Message text={error} />{!!error && <Button title="Tentar novamente" onPress={() => { setBusy(true); setError(''); setRetry(v => v + 1); }} />}
    {role && !busy && !error && <><View style={{ borderRadius: 20, overflow: 'hidden' }}><RoleIllustration name={role.nome} category={role.categoria} height={230} /></View><Text style={s.title}>{role.nome}</Text><View style={[s.chip, s.selected, { alignSelf: 'flex-start' }]}><Text style={s.selectedText}>{role.categoria ?? 'Sem categoria'}</Text></View><Text style={s.text}>{price(role.valor)}</Text><Text style={s.text}>{role.descricao ?? 'Descrição não informada.'}</Text><Text style={s.label}>Onde</Text><Text style={s.subtitle}>{role.local ?? 'Local não informado'} • {role.cidade}</Text><Text style={s.label}>Quando</Text><Text style={s.subtitle}>{role.data ? role.data.split('-').reverse().join('/') : 'Data não informada'}</Text></>}
    {role && !busy && !error && <>
      <Text style={s.label}>Início do evento: {role.horario ?? 'não informado'}</Text>
      <Text style={s.label}>Término do evento: {role.horarioFim ?? 'não informado'}{role.dataFim ? ` em ${role.dataFim.split('-').reverse().join('/')}` : ''}</Text>
      <Text style={s.small}>Horários locais informados no cadastro do evento. A duração da sua visita é uma estimativa e não confirma reserva ou disponibilidade.</Text>
      <Text style={s.label}>Funcionamento desta atividade</Text>
      <Text style={s.text}>{describeCharacteristics(role.caracteristicas)}</Text>
      <Text style={s.text}>{describeHours(role.funcionamento)}</Text>
      {!!role.funcionamento?.length && <Text style={s.small}>Exceções por data substituem a semana. Dias não listados precisam de confirmação. Os horários não garantem vagas.</Text>}
      {role.cancelado && <Message text={`Evento cancelado. ${role.motivoCancelamento ?? ''}`} />}
      <Button disabled={role.cancelado} title={included ? 'Ver em Organizar rolê' : 'Adicionar rolê'} onPress={() => {
        if (!included) {
          const problem = additionProblem(saved ? [] : stops, role, saved ? '' : plan.date);
          if (problem) { setSelectionError(problem); return; }
          setSelectionError('');
          if (saved) startNew();
          setPlan(previous => ({ ...previous, city: role.cidade }));
          setStops(previous => appendStop(saved ? [] : previous, role)); setSaved(null);
        }
        router.replace('/organize');
      }} />
      {token && !demo && <Button title="Avaliações de visitantes" secondary onPress={() => router.push({ pathname: '/community-texts', params: { tipo: 'AVALIACAO', alvo: String(role.id) } })} />}
      <Message text={selectionError} />
      {!!selectionError && <Button title="Revisar paradas em Organizar rolê" secondary onPress={() => router.push('/organize')} />}
      <Text style={s.small}>Adicionar não faz reserva nem compra ingresso. As escolhas ficam nesta sessão até você salvar o roteiro na conta.</Text>
    </>}
  </ScrollView>;
}
