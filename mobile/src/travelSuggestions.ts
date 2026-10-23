import type { AutomaticSuggestion, SuggestionSettings } from './automaticSuggestions';

export type TravelFix = { timestamp: number; coords: { latitude: number; longitude: number; accuracy: number | null } };
export function usableTravelFix(fix: TravelFix | undefined, now: number): fix is TravelFix {
  return !!fix && Number.isFinite(fix.timestamp) && now >= fix.timestamp && now - fix.timestamp < 120000
    && Number.isFinite(fix.coords.latitude) && Math.abs(fix.coords.latitude) <= 90
    && Number.isFinite(fix.coords.longitude) && Math.abs(fix.coords.longitude) <= 180
    && fix.coords.accuracy !== null && Number.isFinite(fix.coords.accuracy) && fix.coords.accuracy >= 0 && fix.coords.accuracy <= 500;
}
export type TravelDependencies = {
  allowed: () => Promise<boolean>; permissions: () => Promise<boolean>; stop: () => Promise<void>;
  settings: () => Promise<SuggestionSettings>; city: (fix: TravelFix) => Promise<string | null>;
  evaluate: (fix: TravelFix, city: string) => Promise<AutomaticSuggestion | null>;
  notify: (suggestion: AutomaticSuggestion) => Promise<void>;
};
// Recheck consent after asynchronous work; never notify for a logged-out or replaced session.
export async function evaluateTravel(fix: TravelFix | undefined, now: number, deps: TravelDependencies) {
  if (!await deps.allowed() || !await deps.permissions()) { await deps.stop(); return; }
  if (!usableTravelFix(fix, now)) return;
  const settings = await deps.settings();
  if (!settings.ativo || !settings.viagens) { await deps.stop(); return; }
  const city = await deps.city(fix);
  if (!city?.trim() || city.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase() === settings.cidadeBase?.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase()) return;
  if (!await deps.allowed() || !await deps.permissions()) return;
  const suggestion = await deps.evaluate(fix, city);
  if (suggestion?.outraCidade && await deps.allowed() && await deps.permissions()) await deps.notify(suggestion);
}
