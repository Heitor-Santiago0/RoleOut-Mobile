import { offlineItineraries } from './offlineItineraries';
export type Account = { id: number; nome: string; email: string; emailConfirmado: boolean; administrador: boolean };
export type Role = { caracteristicas?: import("./roleCharacteristics").Characteristics | null; funcionamento?: import("./openingHours").HoursRule[] | null; id: number; nome: string; descricao: string | null; local: string | null; cidade: string; data: string | null; categoria: string | null; valor: number; latitude?: number | null; horario?: string | null; horarioFim?: string | null; dataFim?: string | null; cancelado?: boolean; motivoCancelamento?: string | null; longitude?: number | null };
export type Session = { token: string; expiraEm: string; conta: Account };
export type ItinerarySummary = { id: number; nome: string; cidade: string; data: string; partida: string; pessoas: number; duracaoMinutos: number; orcamentoCentavos: number; modoOrcamento: 'person' | 'group'; totalCentavos: number; criadoEm: string; totalParadas?: number; paradasConcluidas?: number };
export type SavedItinerary = { roteiro: ItinerarySummary; paradas: { roleId: number; nome: string; local: string | null; minutos: number; precoCentavos: number; horario?: string | null; concluida?: boolean; cancelado?: boolean }[] };
export const apiUrl = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, '') ?? '';
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export async function request<T>(path: string, options: { method?: string; body?: unknown; token?: string; timeoutMs?: number } = {}): Promise<T> {
  if (!apiUrl) throw new Error('Conexão com o servidor não configurada.');
  if (!__DEV__ && !apiUrl.startsWith('https://')) throw new Error('A conexão precisa usar HTTPS.');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), Math.min(60000, options.timeoutMs ?? 10000));
  try {
    const response = await fetch(`${apiUrl}${path}`, {
      method: options.method ?? 'GET', signal: controller.signal,
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}) },
      ...(options.body ? { body: JSON.stringify(options.body) } : {}),
    });
    if (response.status === 204) {
      if (options.token && options.method === 'DELETE' && /^\/roteiros\/\d+$/.test(path)) {
        await offlineItineraries.remove(options.token, Number(path.split('/')[2])).catch(() => {});
      }
      return undefined as T;
    }
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new ApiError(response.status, body?.mensagem ?? 'Não foi possível concluir a operação.');
    if (body === null) throw new Error('Resposta inválida do servidor.');
    if (options.token && (/^(?:\/roteiros(?:\/|$)|\/comunidade\/copiar\/)/.test(path)) && body.roteiro && Array.isArray(body.paradas)) {
      // A device-storage failure must not turn a successful server save into a retry/duplicate.
      await offlineItineraries.save(options.token, body as SavedItinerary).catch(() => {});
    }
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
