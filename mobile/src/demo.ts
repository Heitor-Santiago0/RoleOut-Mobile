import type { Role } from './api';
export const demoRoles: Role[] = [
  { id: 1, nome: 'Visita ao museu', categoria: 'Cultura', local: 'Museu de demonstração', cidade: 'Uberaba', data: null, valor: 0, descricao: 'Exemplo fictício para demonstrar a navegação. Este cadastro não representa um museu real.' },
  { id: 2, nome: 'Noite de música', categoria: 'Música', local: 'Espaço de demonstração', cidade: 'Uberaba', data: '2026-10-15', valor: 30, descricao: 'Atividade fictícia de desenvolvimento. Data, local e preço são exemplos.' },
  { id: 3, nome: 'Sabores da cidade', categoria: 'Gastronomia', local: 'Restaurante de demonstração', cidade: 'Uberaba', data: null, valor: 45, descricao: 'Exemplo fictício de uma experiência gastronômica.' },
  { id: 4, nome: 'Cinema em boa companhia', categoria: 'Cultura', local: 'Cinema de demonstração', cidade: 'Uberaba', data: null, valor: 20, descricao: 'Exemplo fictício de um passeio para testar a interface.' },
];
