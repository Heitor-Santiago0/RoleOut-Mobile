import type { SavedItinerary } from './api';

export type OfflineEntry = { savedAt: string; data: SavedItinerary };
type Storage = { get: (key: string) => Promise<string | null>; set: (key: string, value: string) => Promise<void>; remove: (key: string) => Promise<void> };
type Pointer = { version: 1; token: string; revision: string; count: number };
const indexKey = 'roleout.offline.index';
const partKey = (revision: string, index: number) => `roleout.offline.${revision}.${index}`;

// Small encrypted chunks avoid platform-specific SecureStore value limits.
// Commit the pointer last: an interrupted write keeps the previous snapshot usable.
export function createOfflineStore(storage: Storage, currentToken: () => Promise<string | null>) {
  let queue: Promise<unknown> = Promise.resolve();
  function serial<T>(action: () => Promise<T>): Promise<T> {
    const result = queue.then(action);
    queue = result.catch(() => {});
    return result;
  }
  async function pointer(): Promise<Pointer | null> {
    try {
      const value = JSON.parse(await storage.get(indexKey) ?? 'null');
      return value?.version === 1 && typeof value.token === 'string' && /^[a-z0-9-]+$/.test(value.revision)
        && Number.isInteger(value.count) && value.count > 0 && value.count <= 20000 ? value : null;
    } catch { return null; }
  }
  async function discard(value: Pointer | null) {
    if (value) for (let i = 0; i < value.count; i++) await storage.remove(partKey(value.revision, i)).catch(() => {});
  }
  async function read(token: string): Promise<OfflineEntry[]> {
    if (!token || await currentToken() !== token) return [];
    const value = await pointer();
    if (!value || value.token !== token) return [];
    try {
      let text = '';
      for (let i = 0; i < value.count; i++) {
        const part = await storage.get(partKey(value.revision, i));
        if (part === null) return [];
        text += part;
      }
      const entries = JSON.parse(text);
      if (!Array.isArray(entries) || !entries.every(entry => typeof entry.savedAt === 'string'
        && Number.isSafeInteger(entry.data?.roteiro?.id) && Array.isArray(entry.data?.paradas))) return [];
      return await currentToken() === token ? entries : [];
    } catch { return []; }
  }
  async function write(token: string, entries: OfflineEntry[]) {
    if (await currentToken() !== token) return;
    const old = await pointer();
    // Escape non-ASCII characters so each chunk is at most 1500 UTF-8 bytes.
    const text = JSON.stringify(entries).replace(/[\u007f-\uffff]/g, char => `\\u${char.charCodeAt(0).toString(16).padStart(4, '0')}`);
    const next: Pointer = { version: 1, token, revision: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`, count: Math.ceil(text.length / 1500) };
    if (next.count > 20000) throw new Error('Não há espaço reservado para tantos roteiros offline.');
    try {
      for (let i = 0; i < next.count; i++) await storage.set(partKey(next.revision, i), text.slice(i * 1500, (i + 1) * 1500));
      if (await currentToken() !== token) { await discard(next); return; }
      await storage.set(indexKey, JSON.stringify(next));
    } catch (error) { await discard(next); throw error; }
    await discard(old);
  }
  return {
    read: (token: string) => serial(() => read(token)),
    save: (token: string, data: SavedItinerary) => serial(async () => {
      const entries = await read(token);
      await write(token, [{ data, savedAt: new Date().toISOString() }, ...entries.filter(entry => entry.data.roteiro.id !== data.roteiro.id)]);
    }),
    remove: (token: string, id: number) => serial(async () => write(token, (await read(token)).filter(entry => entry.data.roteiro.id !== id))),
    retain: (token: string, ids: number[]) => serial(async () => write(token, (await read(token)).filter(entry => ids.includes(entry.data.roteiro.id)))),
    clear: () => serial(async () => { const old = await pointer(); await storage.remove(indexKey); await discard(old); }),
  };
}
