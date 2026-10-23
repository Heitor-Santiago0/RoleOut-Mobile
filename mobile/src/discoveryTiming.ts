import { happensOn } from './eventPeriod.ts';
import { hoursOn, type HoursRule } from './openingHours.ts';

type Schedule = { data?: string | null; dataFim?: string | null; horario?: string | null; horarioFim?: string | null; funcionamento?: HoursRule[] | null; cancelado?: boolean };
export type DiscoveryTiming = { status: 'now' | 'later' | 'unknown' | 'unavailable'; label: string };
const minutes = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3));
const clock = (minute: number) => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;

// Published local schedules, not a live occupancy or availability signal.
export function discoveryTiming(role: Schedule, day: string, time: string): DiscoveryTiming {
  const unavailable: DiscoveryTiming = { status: 'unavailable', label: 'Sem horário disponível hoje' };
  if (role.cancelado || !happensOn(role, day)) return unavailable;
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) return { status: 'unknown', label: 'Horário a confirmar' };
  const now = minutes(time);
  const start = role.horario && (!role.data || role.data === day) ? minutes(role.horario) : 0;
  const end = role.horarioFim && (!role.data || (role.dataFim || role.data) === day) ? minutes(role.horarioFim) : 1440;
  const hours = hoursOn(role.funcionamento, day);
  const windows = (hours ?? [{ inicio: '00:00', fim: '24:00' }])
    .map(w => ({ start: Math.max(start, minutes(w.inicio)), end: Math.min(end, minutes(w.fim)) }))
    .filter(w => w.end > Math.max(now, w.start)).sort((a, b) => a.start - b.start);
  if (!windows.length) return unavailable;
  if (windows[0].start > now) return { status: 'later', label: `Hoje a partir de ${clock(windows[0].start)}` };
  if (hours !== undefined || (role.horario && role.horarioFim)) return { status: 'now', label: 'Neste horário, conforme a programação' };
  return { status: 'unknown', label: 'Funcionamento a confirmar' };
}
