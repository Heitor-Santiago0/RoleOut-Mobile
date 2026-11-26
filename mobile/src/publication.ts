export type PublicationPhoto = { base64: string; legenda: string };
export type PublicationStop = { roleId: number; nome: string; local: string | null; minutos: number };
export type OwnPublication = { id: string | null; titulo: string; relato: string; cidade: string; paradas: PublicationStop[]; fotos: PublicationPhoto[]; publico: boolean; versao: number };
export type PublicPublication = Omit<OwnPublication, 'publico' | 'versao' | 'id'> & { id: string; autor: { id: string; nome: string; apresentacao: string } };
export type PublicationPage = { itens: { id: string; titulo: string; cidade: string; totalParadas: number; totalFotos: number }[]; proximo: string | null };
export function publicationError(value: OwnPublication): string {
  if (!value.titulo.trim() || value.titulo.trim().length > 120) return 'Escreva um título de até 120 caracteres.';
  if (value.relato.trim().length > 3000) return 'Seu relato pode ter até 3.000 caracteres.';
  if (value.fotos.length > 3) return 'Escolha até três fotos.';
  if (value.fotos.some(photo => !photo.base64 || photo.base64.length > 2796204 || photo.legenda.trim().length > 200)) return 'Use fotos de até 2 MB e legendas de até 200 caracteres.';
  return '';
}
// Explicit allow-list: never send route dates, participants, budget, identity or current location.
export function publicationInput(value: OwnPublication, publico: boolean) {
  return { id: value.id, titulo: value.titulo.trim(), relato: value.relato.trim(), publico, versao: value.versao,
    fotos: value.fotos.map(photo => ({ base64: photo.base64, legenda: photo.legenda.trim() })) };
}
export function publicationVersion(value: OwnPublication) { return { id: value.id, versao: value.versao }; }
