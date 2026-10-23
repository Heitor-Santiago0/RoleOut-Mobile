import test from 'node:test';
import assert from 'node:assert/strict';
import { createOfflineStore } from '../src/offlineStore.ts';

const route = (id, nome = 'Passeio') => ({ roteiro: { id, nome, cidade: 'Uberaba', data: '2026-10-15', partida: '18:00', pessoas: 1, totalCentavos: 1000 }, paradas: [{ roleId: 7, nome: 'Café', local: 'Rua A', minutos: 40, precoCentavos: 1000, horario: '18:00', concluida: false }] });
function fixture() {
  const values = new Map();
  const state = { token: 'account-a', fail: false };
  const storage = {
    get: async key => values.get(key) ?? null,
    set: async (key, value) => { if (state.fail && key !== 'roleout.offline.index') throw new Error('disk full'); assert.ok(Buffer.byteLength(value) <= 2048); values.set(key, value); },
    remove: async key => { values.delete(key); },
  };
  return { values, state, storage, store: createOfflineStore(storage, async () => state.token) };
}
test('offline copies survive a new store instance and preserve ordered stops and times', async () => {
  const f = fixture();
  const data = route(1, 'Música 🎵 '.repeat(1000));
  await f.store.save('account-a', data);
  const reopened = createOfflineStore(f.storage, async () => f.state.token);
  const entries = await reopened.read('account-a');
  assert.deepEqual(entries[0].data, data);
  assert.ok(Number.isFinite(Date.parse(entries[0].savedAt)));
});
test('offline data never crosses session boundaries, including stale writes', async () => {
  const f = fixture();
  await f.store.save('account-a', route(1));
  f.state.token = 'account-b';
  assert.deepEqual(await f.store.read('account-a'), []);
  assert.deepEqual(await f.store.read('account-b'), []);
  await f.store.save('account-a', route(2));
  await f.store.save('account-b', route(3));
  assert.deepEqual((await f.store.read('account-b')).map(e => e.data.roteiro.id), [3]);
});
test('a failed write retains the previous usable copy', async () => {
  const f = fixture();
  await f.store.save('account-a', route(1));
  f.state.fail = true;
  await assert.rejects(f.store.save('account-a', route(2)));
  assert.deepEqual((await f.store.read('account-a')).map(e => e.data.roteiro.id), [1]);
});
test('concurrent updates serialize, replace snapshots, prune server deletions, and clear on logout', async () => {
  const f = fixture();
  await Promise.all([f.store.save('account-a', route(1)), f.store.save('account-a', route(2))]);
  await f.store.save('account-a', route(1, 'Renomeado'));
  assert.equal((await f.store.read('account-a')).length, 2);
  await f.store.retain('account-a', [1]);
  assert.equal((await f.store.read('account-a'))[0].data.roteiro.nome, 'Renomeado');
  await f.store.remove('account-a', 1);
  assert.deepEqual(await f.store.read('account-a'), []);
  await f.store.clear();
  assert.equal(f.values.size, 0);
});
test('missing or corrupt chunks fail closed instead of rendering partial private data', async () => {
  const f = fixture();
  await f.store.save('account-a', route(1));
  const index = JSON.parse(f.values.get('roleout.offline.index'));
  f.values.delete(`roleout.offline.${index.revision}.0`);
  assert.deepEqual(await f.store.read('account-a'), []);
  f.values.set('roleout.offline.index', 'broken');
  assert.deepEqual(await f.store.read('account-a'), []);
});
