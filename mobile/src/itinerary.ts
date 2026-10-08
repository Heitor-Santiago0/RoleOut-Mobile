import type { Role } from './api';
export type Stop = { role: Role; minutes: number };
export type Plan = { city: string; date: string; start: string; hours: string; people: string; budget: string; budgetMode?: 'person' | 'group' };
export function formatDateInput(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4)].filter(Boolean).join('/');
}
export function groupBudgetCents(plan: Plan): number | null {
  const amount = moneyCents(plan.budget);
  const people = Number(plan.people);
  if (amount === null || !Number.isInteger(people) || people < 1 || people > 50) return null;
  return plan.budgetMode === 'person' ? amount * people : amount;
}
export function moneyCents(value: string): number | null {
  if (!/^\d{1,7}([.,]\d{1,2})?$/.test(value.trim())) return null;
  const [whole, fraction = ''] = value.trim().replace(',', '.').split('.');
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
}
export function isoDate(value: string): string | null {
  if (!/^\d{2}\/\d{2}\/\d{4}$/.test(value)) return null;
  const [day, month, year] = value.split('/').map(Number);
  if (year < 2000 || year > 2100) return null;
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
    ? year + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0') : null;
}
export function validatePlan(plan: Plan, stage: 1 | 2): string {
  if (stage === 1) {
    if (!plan.city.trim() || plan.city.trim().length > 150) return 'Informe uma cidade com até 150 caracteres.';
    if (!isoDate(plan.date.trim())) return 'Informe uma data válida no formato DD/MM/AAAA.';
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(plan.start.trim())) return 'Informe a partida no formato HH:MM (24 horas).';
    if (!/^\d{1,2}$/.test(plan.hours) || Number(plan.hours) < 1 || Number(plan.hours) > 12) return 'Escolha uma duração de 1 a 12 horas.';
  } else {
    if (!/^\d{1,2}$/.test(plan.people) || Number(plan.people) < 1 || Number(plan.people) > 50) return 'Informe de 1 a 50 pessoas.';
    if (moneyCents(plan.budget) === null) return 'Informe um orçamento válido, com até duas casas decimais. Use zero para opções gratuitas.';
  }
  return '';
}
export function totalCents(stops: Stop[], people: number): number {
  return stops.reduce((sum, stop) => sum + Math.round(stop.role.valor * 100) * people, 0);
}
export function candidates(roles: Role[], categories: string[], date: string, people: number, budget: number): Role[] {
  const selected = categories.map(value => value.toLowerCase());
  return roles.filter(role => selected.includes((role.categoria ?? '').trim().toLowerCase())
    && (!role.data || role.data === date) && Number.isFinite(role.valor) && role.valor >= 0
    && Math.round(role.valor * 100) * people <= budget)
    .sort((a, b) => a.valor - b.valor || a.id - b.id);
}
export function planProblem(stops: Stop[], people: number, budget: number, minutes: number): string {
  if (stops.some(stop => !Number.isFinite(stop.role.valor) || stop.role.valor < 0)) return 'Uma opção tem preço inválido. Atualize a busca.';
  if (new Set(stops.map(stop => stop.role.id)).size !== stops.length) return 'Essa parada já está no roteiro.';
  if (totalCents(stops, people) > budget) return 'Essa alteração ultrapassa o orçamento total. Remova uma parada ou revise o orçamento.';
  if (stops.reduce((sum, stop) => sum + stop.minutes, 0) > minutes) return 'Essa alteração ultrapassa a duração escolhida. Reduza o tempo de uma parada ou revise a duração.';
  return '';
}
export function schedule(stops: Stop[], start: string) {
  const [hours, minutes] = start.trim().split(':').map(Number);
  let cursor = hours * 60 + minutes;
  function label(value: number) {
    return String(Math.floor(value / 60) % 24).padStart(2, '0') + ':' + String(value % 60).padStart(2, '0') + (value >= 1440 ? ' (+1 dia)' : '');
  }
  return stops.map(stop => { const from = cursor; cursor += stop.minutes; return { ...stop, from: label(from), to: label(cursor) }; });
}
