import type { ItinerarySummary } from './api';

export function itineraryStatus(item: Pick<ItinerarySummary, 'data' | 'partida' | 'totalParadas' | 'paradasConcluidas'>, now: Date) {
  const total = item.totalParadas ?? 0;
  const completed = item.paradasConcluidas ?? 0;
  if (total > 0 && completed >= total) return { label: 'Concluído', action: 'Ver resumo', color: '#9BE0BA' };
  if (completed > 0) return { label: 'Em andamento', action: 'Continuar rolê', color: '#9FC5FF' };
  const [year, month, day] = item.data.split('-').map(Number);
  const [hours, minutes] = item.partida.split(':').map(Number);
  const departure = new Date(year, month - 1, day, hours, minutes);
  if (departure.getTime() > now.getTime()) return { label: 'Agendado', action: 'Ver rolê agendado', color: '#E2C4FF' };
  const today = year === now.getFullYear() && month === now.getMonth() + 1 && day === now.getDate();
  return today ? { label: 'Pronto para ir', action: 'Abrir rolê', color: '#FFE1A0' }
    : { label: 'Pendente', action: 'Rever rolê', color: '#BAC1D5' };
}
