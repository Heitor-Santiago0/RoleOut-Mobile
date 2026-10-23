type EventPeriod = { data?: string | null; dataFim?: string | null; horario?: string | null; horarioFim?: string | null };
export function happensOn(event: EventPeriod, day: string): boolean {
  return !event.data || (day >= event.data && day <= (event.dataFim || event.data));
}
export function eventStartOn(event: EventPeriod, day?: string | null): string | null | undefined {
  return day && event.data && day > event.data ? null : event.horario;
}
export function eventEndMinute(event: EventPeriod, day?: string | null): number {
  if (!event.horarioFim) return Infinity;
  const clock = Number(event.horarioFim.slice(0, 2)) * 60 + Number(event.horarioFim.slice(3));
  const start = day || event.data;
  return clock + (event.dataFim && start ? (Date.parse(event.dataFim) - Date.parse(start)) / 86400000 * 1440 : 0);
}
export function periodsOverlap(events: EventPeriod[]): boolean {
  const dated = events.filter(event => !!event.data);
  return dated.length === 0 || dated.map(event => event.data!).sort().at(-1)! <= dated.map(event => event.dataFim || event.data!).sort()[0];
}
