export type Interests = { status: string; musica: string[]; comida: string[]; atividades: string[]; ambientes: string[] };
type Candidate = { id: number; categoria: string | null; data: string | null };
const normalized = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();
const activityCategories: Record<string, string[]> = {
  'Música ao vivo': ['musica'], Cinema: ['cinema', 'cultura'], Museus: ['museu', 'museus', 'cultura'],
  Gastronomia: ['gastronomia'], 'Passeios ao ar livre': ['natureza'], Esportes: ['esporte', 'esportes'],
};

/** Broad category affinity only; never infer venue amenities, menu or music genre. */
export function preferenceReasons(role: Candidate, preferences: Interests, today: string): string[] {
  if (preferences.status !== 'CONCLUIDO' || !role.categoria || (role.data !== null && role.data < today)) return [];
  const category = normalized(role.categoria);
  const reasons: string[] = [];
  for (const activity of preferences.atividades) {
    if (activityCategories[activity]?.includes(category)) reasons.push(`Seu interesse em ${activity} se relaciona à categoria ${role.categoria}.`);
  }
  if (category === 'musica' && preferences.musica.length > 0) reasons.push('Você escolheu estilos musicais; este rolê está na categoria Música. O gênero não foi informado.');
  if (category === 'gastronomia' && preferences.comida.length > 0) reasons.push('Você escolheu preferências de comida; este rolê está na categoria Gastronomia. O tipo de cozinha não foi informado.');
  if (category === 'cultura' && preferences.ambientes.includes('Cultural')) reasons.push('Seu interesse em ambientes culturais se relaciona à categoria Cultura.');
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
