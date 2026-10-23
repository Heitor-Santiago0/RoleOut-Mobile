import { happensOn } from './eventPeriod.ts';
import { discoveryTiming } from './discoveryTiming.ts';
import type { HoursRule } from './openingHours.ts';
import type { Characteristics } from './roleCharacteristics.ts';
export type Interests = { status: string; musica: string[]; comida: string[]; atividades: string[]; ambientes: string[] };
type Candidate = { caracteristicas?: Characteristics | null; id: number; categoria: string | null; data: string | null; dataFim?: string | null; cancelado?: boolean };
const normalized = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();
const activityCategories: Record<string, string[]> = {
  'Música ao vivo': ['musica'], Cinema: ['cinema', 'cultura'], Museus: ['museu', 'museus', 'cultura'],
  Gastronomia: ['gastronomia'], 'Passeios ao ar livre': ['natureza'], Esportes: ['esporte', 'esportes'],
};

/** Exact declared attributes first; empty groups allow clearly labelled category fallback. */
export function exactPreferenceReasons(role: Pick<Candidate, 'caracteristicas'>, preferences: Interests): string[] {
  if (preferences.status !== 'CONCLUIDO') return [];
  const labels = { musica: 'Música', comida: 'Culinária', ambientes: 'Ambiente' };
  return (['musica', 'comida', 'ambientes'] as const).flatMap(key => {
    const selected = new Set(preferences[key].map(normalized));
    const matches = [...new Set(role.caracteristicas?.[key] ?? [])].filter(v => selected.has(normalized(v)));
    return matches.length ? [labels[key] + ': ' + matches.join(', ') + ' — informado pelo responsável e presente nos seus interesses.'] : [];
  });
}
export function preferenceReasons(role: Candidate, preferences: Interests, today: string): string[] {
  if (role.cancelado || preferences.status !== 'CONCLUIDO' || !role.categoria || (role.data !== null && (role.dataFim || role.data) < today)) return [];
  const category = normalized(role.categoria);
  const reasons: string[] = exactPreferenceReasons(role, preferences);
  for (const activity of preferences.atividades) {
    if (activityCategories[activity]?.includes(category)) reasons.push(`Seu interesse em ${activity} se relaciona à categoria ${role.categoria}.`);
  }
  if (category === 'musica' && preferences.musica.length > 0 && !role.caracteristicas?.musica?.length) reasons.push('Você escolheu estilos musicais; este rolê está na categoria Música. O gênero não foi informado.');
  if (category === 'gastronomia' && preferences.comida.length > 0 && !role.caracteristicas?.comida?.length) reasons.push('Você escolheu preferências de comida; este rolê está na categoria Gastronomia. O tipo de cozinha não foi informado.');
  if (category === 'cultura' && preferences.ambientes.includes('Cultural') && !role.caracteristicas?.ambientes?.length) reasons.push('Seu interesse em ambientes culturais se relaciona à categoria Cultura.');
  return [...new Set(reasons)];
}

export function preferenceHighlights<T extends Candidate>(items: T[], preferences: Interests, today: string) {
  const seen = new Set<number>();
  return items.flatMap(role => {
    if (seen.has(role.id)) return [];
    seen.add(role.id);
    const reasons = preferenceReasons(role, preferences, today);
    return reasons.length ? [{ role, reasons }] : [];
  }).slice(0, 3);
}

export function nearbyHighlights<T extends { caracteristicas?: Characteristics | null; id: number; categoria: string | null; data?: string | null; dataFim?: string | null; horario?: string | null; horarioFim?: string | null; funcionamento?: HoursRule[] | null; cancelado?: boolean; distanciaKm: number }>(items: T[], preferences: Interests, today: string, time?: string) {
  const priority = { now: 0, later: 1, unknown: 2, unavailable: 3 };
  const timing = (item: T) => discoveryTiming(item, today, time ?? '');
  const eligible = items.filter(item => !item.cancelado && Number.isFinite(item.distanciaKm) && item.distanciaKm >= 0 && item.distanciaKm <= 5 && happensOn(item, today) && (!time || timing(item).status !== 'unavailable'));
  eligible.sort((a, b) => (time ? priority[timing(a).status] - priority[timing(b).status] : 0) || exactPreferenceReasons(b, preferences).length - exactPreferenceReasons(a, preferences).length || Number(b.data === today) - Number(a.data === today) || a.distanciaKm - b.distanciaKm || a.id - b.id);
  return preferenceHighlights(eligible.map(item => ({ ...item, data: item.data ?? null })), preferences, today);
}
