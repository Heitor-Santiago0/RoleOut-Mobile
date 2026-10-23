export type Characteristics = { musica: string[]; comida: string[]; ambientes: string[] };
export const characteristicSections = [
  { key: 'musica', title: 'Gêneros musicais' },
  { key: 'comida', title: 'Culinária' },
  { key: 'ambientes', title: 'Ambiente' },
] as const;
export function describeCharacteristics(value?: Characteristics | null): string {
  return characteristicSections.map(({ key, title }) => title + ': ' + (value?.[key]?.length ? value[key].join(', ') : 'Não informado')).join('\n');
}
