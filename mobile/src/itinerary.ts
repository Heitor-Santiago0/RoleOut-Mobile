import { openingProblem } from './openingHours.ts';
import { happensOn, eventStartOn, eventEndMinute, periodsOverlap } from './eventPeriod.ts';
import type { Role } from './api';
export type Stop = { role: Role; minutes: number; horario?: string };
export type Plan = { name?: string; city: string; date: string; start: string; hours: string; people: string; budget: string; budgetMode?: 'person' | 'group' };
export function itineraryName(city: string, date: string, name = ''): string {
  if (name.trim().length > 200) throw new Error('Use até 200 caracteres para o nome do rolê.');
  return name.trim() || `Rolê em ${city.trim()} — ${date}`;
}
export function selectionProblem(stops: Stop[], plan: Plan): string {
  if (stops.some(stop => stop.role.cancelado)) return 'Há um evento cancelado no roteiro. Remova ou troque essa parada.';
  if (stops.some(stop => stop.role.cidade.trim().toLowerCase() !== plan.city.trim().toLowerCase())) return 'Escolha rolês da mesma cidade do roteiro.';
  const date = isoDate(plan.date.trim());
  if (date && stops.some(stop => !happensOn(stop.role, date))) return 'Há um rolê com data diferente da escolhida. Revise a data ou remova essa parada.';
  return '';
}
export function appendStop(stops: Stop[], role: Role): Stop[] {
  return stops.some(stop => stop.role.id === role.id) ? stops : [...stops, { role, minutes: 60 }];
}
export function additionProblem(stops: Stop[], role: Role, date = ''): string {
  if (role.cancelado) return 'Este evento foi cancelado e não pode ser adicionado.';
  const city = role.cidade.trim().toLocaleLowerCase('pt-BR');
  if (stops.some(stop => stop.role.cidade.trim().toLocaleLowerCase('pt-BR') !== city)) {
    return `Esse rolê fica em ${role.cidade}. Seu roteiro já contém paradas de outra cidade. Revise as paradas em Organizar rolê antes de adicionar.`;
  }
  const day = isoDate(date);
  const events = [...stops.map(stop => stop.role), role];
  if (day ? events.some(event => !happensOn(event, day)) : !periodsOverlap(events)) return 'Essas paradas acontecem em datas diferentes. Crie outro rolê para o outro dia.';
  return '';
}
export function formatTimeInput(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  return digits.length > 2 ? `${digits.slice(0, 2)}:${digits.slice(2)}` : digits;
}
export function replacementProblem(stops: Stop[], targetId: number, role: Role, plan: Plan): string {
  if (role.cancelado) return 'Este evento foi cancelado. Escolha outra opção.';
  const normalized = (value: string) => value.trim().toLocaleLowerCase('pt-BR');
  const target = stops.find(stop => stop.role.id === targetId);
  if (!target) return 'Essa parada não está mais no roteiro.';
  if (stops.some(stop => stop.role.id === role.id)) return 'Esse rolê já está no roteiro.';
  if (normalized(role.cidade) !== normalized(target.role.cidade)) return 'Escolha uma opção da mesma cidade.';
  if (target.role.categoria && normalized(role.categoria ?? '') !== normalized(target.role.categoria)) return 'Escolha uma opção da mesma categoria.';
  const date = isoDate(plan.date.trim());
  if (date && !happensOn(role, date)) return 'Esse rolê acontece em outra data.';
  if (!Number.isFinite(role.valor) || role.valor < 0) return 'O preço dessa opção precisa ser atualizado.';
  return '';
}
export function replaceStop(stops: Stop[], targetId: number, role: Role, plan: Plan): Stop[] {
  const problem = replacementProblem(stops, targetId, role, plan);
  if (problem) throw new Error(problem);
  return stops.map(stop => stop.role.id === targetId ? { ...stop, role } : stop);
}
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
    if ((plan.name?.trim().length ?? 0) > 200) return 'Use até 200 caracteres para o nome do rolê.';
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
  return roles.filter(role => !role.cancelado && selected.includes((role.categoria ?? '').trim().toLowerCase())
    && happensOn(role, date) && Number.isFinite(role.valor) && role.valor >= 0
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
export function schedule(stops: Stop[], start: string, day?: string | null) {
  const [hours, minutes] = start.trim().split(':').map(Number);
  let cursor = hours * 60 + minutes;
  function label(value: number) {
    return String(Math.floor(value / 60) % 24).padStart(2, '0') + ':' + String(value % 60).padStart(2, '0') + (value >= 1440 ? ' (+1 dia)' : '');
  }
  return stops.map(stop => { const fixed = [stop.horario, eventStartOn(stop.role, day)].filter((time): time is string => !!time && /^([01]\d|2[0-3]):[0-5]\d$/.test(time)).sort()[0]; if (fixed) { const [h, m] = fixed.split(':').map(Number); cursor = Math.max(cursor, h * 60 + m); } const from = cursor; cursor += stop.minutes; return { ...stop, from: label(from), to: label(cursor) }; });
}

/** Uses event data already loaded; the server rechecks the current catalog on save.
 * No travel time is added without a reliable source.
 */
export function timingCheck(stops: Stop[], start: string, day?: string | null): { minutes: number; problem: string } {
  const parse = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3));
  const valid = (time: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(time);
  if (!valid(start)) return { minutes: 0, problem: 'Informe a partida no formato HH:MM.' };
  const departure = parse(start);
  let cursor = departure;
  for (const stop of stops) {
    if (stop.horario && !valid(stop.horario)) return { minutes: 0, problem: 'Confira o horário de visita de cada parada.' };
    const fixed = [stop.horario, eventStartOn(stop.role, day)].filter((t): t is string => !!t && valid(t)).sort()[0];
    if (fixed) {
      if (cursor > parse(fixed)) return { minutes: 0, problem: 'A ordem das paradas conflita com um horário marcado. Revise o roteiro.' };
      cursor = parse(fixed);
    }
    const hoursIssue = openingProblem(stop.role.funcionamento, day, cursor, cursor + stop.minutes);
    if (hoursIssue) return { minutes: 0, problem: `${stop.role.nome}: ${hoursIssue}` };
    cursor += stop.minutes;
    if (cursor > eventEndMinute(stop.role, day)) return { minutes: 0, problem: `A visita a ${stop.role.nome} ultrapassa o término informado do evento. Reduza a duração ou revise as paradas.` };
  }
  return { minutes: cursor - departure, problem: '' };
}

export function localDeparture(now: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return { date: `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()}`, time: `${pad(now.getHours())}:${pad(now.getMinutes())}` };
}

export function organizeSchedule(stops: Stop[], date: string, time: string, now: Date, scheduled: boolean, name = '') {
  if (!stops.length) throw new Error('Adicione pelo menos uma parada.');
  const day = isoDate(date);
  if (!day || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) throw new Error('Informe uma data válida e um horário no formato HH:MM.');
  const [year, month, dateNumber] = day.split('-').map(Number);
  const [hour, minute] = time.split(':').map(Number);
  if (scheduled && new Date(year, month - 1, dateNumber, hour, minute).getTime() <= now.getTime()) throw new Error('Escolha uma data e um horário futuros.');
  const city = stops[0].role.cidade;
  const issue = selectionProblem(stops, { city, date, start: time, hours: '1', people: '1', budget: '0' });
  if (issue) throw new Error(issue);
  const timing = timingCheck(stops, time, day);
  if (timing.problem) throw new Error(timing.problem);
  const duration = Math.max(60, timing.minutes);
  if (duration > 720 || stops.length > 48) throw new Error('As paradas ultrapassam o limite de 12 horas. Divida em dois rolês.');
  if (stops.some(stop => !Number.isFinite(stop.role.valor) || stop.role.valor < 0)) throw new Error('Atualize os preços dos rolês antes de salvar.');
  return {
    nome: itineraryName(city, date, name), cidade: city, data: day, partida: time,
    duracaoMinutos: duration, pessoas: 1, orcamentoCentavos: totalCents(stops, 1), modoOrcamento: 'person' as const,
    paradas: stops.map(stop => ({ roleId: stop.role.id, minutos: stop.minutes, horario: stop.horario || null })),
  };
}
