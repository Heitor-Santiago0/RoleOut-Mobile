export type Account = { id: number; nome: string; email: string; emailConfirmado: boolean; administrador: boolean };
export type Role = { id: number; nome: string; descricao: string | null; local: string | null; cidade: string; data: string | null; categoria: string | null; valor: number };
export type Session = { token: string; expiraEm: string; conta: Account };
export const apiUrl = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, '') ?? '';
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export async function request<T>(path: string, options: { method?: string; body?: unknown; token?: string } = {}): Promise<T> {
  if (!apiUrl) throw new Error('Conexão com o servidor não configurada.');
  if (!__DEV__ && !apiUrl.startsWith('https://')) throw new Error('A conexão precisa usar HTTPS.');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${apiUrl}${path}`, {
      method: options.method ?? 'GET', signal: controller.signal,
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}) },
      ...(options.body ? { body: JSON.stringify(options.body) } : {}),
    });
    if (response.status === 204) return undefined as T;
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new ApiError(response.status, body?.mensagem ?? 'Não foi possível concluir a operação.');
    if (body === null) throw new Error('Resposta inválida do servidor.');
    return body as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new Error('Não foi possível conectar. Confira sua conexão e tente novamente.');
  } finally { clearTimeout(timer); }
}
export function roles(cidade: string, categoria: string, offset: number, token?: string) {
  const query = new URLSearchParams({ limite: '20', deslocamento: String(offset) });
  if (cidade.trim()) query.set('cidade', cidade.trim());
  if (categoria !== 'Todos') query.set('categoria', categoria);
  return request<{ itens: Role[] }>(`/roles?${query}`, { token });
}
