import { isoDate, moneyCents, validatePlan, type Plan } from './itinerary.ts';
export type CopyStop = { roleId: number; nome: string; minutos: number; horario?: string };
export function copyInput(plan: Plan, stops: CopyStop[]) {
  const error = validatePlan(plan, 1) || validatePlan(plan, 2);
  if (error) throw new Error(error);
  if (!stops.length || stops.length > 48 || new Set(stops.map(p => p.roleId)).size !== stops.length) throw new Error('Escolha de 1 a 48 paradas diferentes.');
  if (stops.some(p => !Number.isInteger(p.minutos) || p.minutos < 15 || p.minutos > 720 || p.minutos % 15 !== 0 || (p.horario && !/^([01]\d|2[0-3]):[0-5]\d$/.test(p.horario)))) throw new Error('Confira a duração e os horários das paradas.');
  if (stops.reduce((sum, p) => sum + p.minutos, 0) > Number(plan.hours) * 60) throw new Error('As paradas ultrapassam a duração total.');
  return { planejamento: { nome: plan.name?.trim(), cidade: plan.city.trim(), data: isoDate(plan.date), partida: plan.start,
    duracaoMinutos: Number(plan.hours) * 60, pessoas: Number(plan.people), orcamentoCentavos: moneyCents(plan.budget), modoOrcamento: plan.budgetMode ?? 'group',
    paradas: stops.map(p => ({ roleId: p.roleId, minutos: p.minutos, horario: p.horario || null })) } };
}
