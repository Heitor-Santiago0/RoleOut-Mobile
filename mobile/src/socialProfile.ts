export type PublicProfile = { id: string; nome: string; apresentacao: string };
export type OwnProfile = Omit<PublicProfile, 'id'> & { id: string | null; publico: boolean; versao: number };
export type ProfilePage = { itens: PublicProfile[]; proximo: string | null };
export function profileError(value: Pick<OwnProfile, 'nome' | 'apresentacao'>) {
  if (!value.nome.trim() || value.nome.trim().length > 80) return 'Escolha um nome público com até 80 caracteres.';
  if (value.apresentacao.trim().length > 500) return 'Use até 500 caracteres na apresentação.';
  return '';
}
export function profileInput(value: OwnProfile, visible = value.publico) {
  return { nome: value.nome.trim(), apresentacao: value.apresentacao.trim(), publico: visible, versao: value.versao };
}
