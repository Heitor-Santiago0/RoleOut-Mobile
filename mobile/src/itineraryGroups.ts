import type { ItinerarySummary } from './api';

export type ItineraryFilter = 'all' | 'active' | 'completed';
export function searchItineraries(items: ItinerarySummary[], query: string) {
  const normalize = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR');
  const terms = normalize(query).trim().split(/\s+/).filter(Boolean);
  return items.filter(item => {
    const text = normalize(`${item.nome} ${item.cidade}`);
    return terms.every(term => text.includes(term));
  });
}
const sections = [
  { key: 'ongoing', title: 'Em andamento' },
  { key: 'today', title: 'Hoje' },
  { key: 'scheduled', title: 'Próximos rolês' },
  { key: 'pending', title: 'Datas anteriores · para revisar' },
  { key: 'completed', title: 'Concluídos' },
] as const;
type SectionKey = typeof sections[number]['key'];
export function itineraryGroup(item: ItinerarySummary, now: Date): SectionKey {
  if ((item.totalParadas ?? 0) > 0 && (item.paradasConcluidas ?? 0) >= item.totalParadas!) return 'completed';
  if ((item.paradasConcluidas ?? 0) > 0) return 'ongoing';
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  return item.data === today ? 'today' : item.data > today ? 'scheduled' : 'pending';
}
export function groupItineraries(items: ItinerarySummary[], now: Date, filter: ItineraryFilter = 'all') {
  return sections.filter(section => filter === 'all' || (filter === 'completed' ? section.key === 'completed' : section.key !== 'completed')).map(section => ({
    ...section,
    items: items.filter(item => itineraryGroup(item, now) === section.key).sort((a, b) => {
      const order = `${a.data}T${a.partida}`.localeCompare(`${b.data}T${b.partida}`);
      return (section.key === 'pending' || section.key === 'completed' ? -order : order) || a.id - b.id;
    }),
  })).filter(section => section.items.length > 0);
}
export function featuredItinerary(items: ItinerarySummary[], now: Date) {
  return groupItineraries(items, now, 'active').find(group => group.key !== 'pending')?.items[0] ?? null;
}

// The server caps each account at 100 plans. Load all pages before grouping so
// a plan created earlier cannot disappear behind a completed first page.
export async function loadItineraryPages(fetchPage: (offset: number) => Promise<ItinerarySummary[]>) {
  const found = new Map<number, ItinerarySummary>();
  for (let offset = 0; offset < 100; offset += 20) {
    const page = await fetchPage(offset);
    for (const item of page) found.set(item.id, item);
    if (page.length < 20) break;
  }
  return [...found.values()];
}
