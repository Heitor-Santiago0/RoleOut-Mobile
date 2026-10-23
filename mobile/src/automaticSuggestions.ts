export type SuggestionSettings = { ativo: boolean; viagens: boolean; intervaloHoras: number; cidadeBase: string | null };
export type DiscoveryContext = { latitude: number; longitude: number; cidade: string; timestamp: number };
export type AutomaticSuggestion = { roleId: number; nome: string; cidade: string; distanciaKm: number; motivos: string[]; outraCidade: boolean };
export const suggestionFrequencies = [{ hours: 0, label: '3 por dia + combinações nos 3 grupos' }, { hours: 24, label: 'No máximo uma por dia' }, { hours: 72, label: 'No máximo uma a cada 3 dias' }, { hours: 168, label: 'No máximo uma por semana' }];
export function freshContext(context: DiscoveryContext | null, now: number): context is DiscoveryContext {
  return !!context && Number.isFinite(context.latitude) && Number.isFinite(context.longitude) && Math.abs(context.latitude) <= 90 && Math.abs(context.longitude) <= 180
    && !!context.cidade.trim() && Number.isFinite(context.timestamp) && now >= context.timestamp && now - context.timestamp < 120000;
}
