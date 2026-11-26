import test from 'node:test';
import assert from 'node:assert/strict';
import { preferenceReasons, preferenceHighlights, nearbyHighlights } from '../src/recommendations.ts';
const preferences = { status: 'CONCLUIDO', musica: [], comida: [], atividades: [], ambientes: [] };
const role = (id, categoria, data = null) => ({ id, categoria, data });
test('nearby recommendations respect radius, today and interests before ranking', () => {
  const items = [
    { ...role(1, 'Cultura'), distanciaKm: 0.1 },
    { ...role(2, 'Cultura', '2026-10-14'), distanciaKm: 2 },
    { ...role(3, 'Cultura', '2026-10-15'), distanciaKm: 0.2 },
    { ...role(4, 'Cultura', '2026-10-13'), distanciaKm: 0.2 },
    { ...role(5, 'Cultura', '2026-10-14'), distanciaKm: 6 },
    { ...role(6, 'Música', '2026-10-14'), distanciaKm: 0.2 },
    { ...role(7, 'Cultura', '2026-10-14'), distanciaKm: 1 },
  ];
  const chosen = { ...preferences, atividades: ['Museus'] };
  assert.deepEqual(nearbyHighlights(items, chosen, '2026-10-14').map(item => item.role.id), [7, 2, 1]);
  assert.deepEqual(nearbyHighlights(items, preferences, '2026-10-14'), []);
  assert.equal(items[0].id, 1);
});
test('category affinity is explicit, accent-insensitive and never asserts a specific activity', () => {
  const result = preferenceReasons(role(1, ' CULTURA '), { ...preferences, atividades: ['Museus'] }, '2026-10-08');
  assert.equal(result.length, 1);
  assert.match(result[0], /se relaciona à categoria/);
  assert.equal(preferenceReasons(role(2, 'MUSICA'), { ...preferences, atividades: ['Música ao vivo'] }, '2026-10-08').length, 1);
});
test('music and food affinities disclose that genre and cuisine are unknown', () => {
  assert.match(preferenceReasons(role(1, 'Música'), { ...preferences, musica: ['Rock'] }, '2026-10-08')[0], /gênero não foi informado/);
  assert.match(preferenceReasons(role(2, 'Gastronomia'), { ...preferences, comida: ['Japonesa'] }, '2026-10-08')[0], /cozinha não foi informado/);
  assert.deepEqual(preferenceReasons(role(3, 'Cultura'), { ...preferences, ambientes: ['Tranquilo', 'Romântico'] }, '2026-10-08'), []);
});
test('skipped preferences, unknown categories and past events are not highlighted', () => {
  const selected = { ...preferences, atividades: ['Museus'] };
  assert.deepEqual(preferenceReasons(role(1, 'Cultura'), { ...selected, status: 'PULADO' }, '2026-10-08'), []);
  assert.deepEqual(preferenceReasons(role(1, null), selected, '2026-10-08'), []);
  assert.deepEqual(preferenceReasons(role(1, 'Cultura', '2026-10-07'), selected, '2026-10-08'), []);
  assert.equal(preferenceReasons(role(1, 'Cultura', '2026-10-08'), selected, '2026-10-08').length, 1);
});
test('highlights use only supplied search results, preserve order, deduplicate and cap at three', () => {
  const items = [role(5, 'Música'), role(3, 'Cultura'), role(3, 'Cultura'), role(2, 'Cultura'), role(4, 'Cultura'), role(1, 'Cultura')];
  const copy = JSON.stringify(items);
  assert.deepEqual(preferenceHighlights(items, { ...preferences, atividades: ['Museus'] }, '2026-10-08').map(item => item.role.id), [3, 2, 4]);
  assert.equal(JSON.stringify(items), copy);
});

const attributes = (musica = [], comida = [], ambientes = []) => ({ musica, comida, ambientes });
test('declared genres, cuisine and ambience explain exact matches without guessing from category', () => {
  const selected = { ...preferences, musica: ['Rock'], comida: ['Japonesa'], ambientes: ['Tranquilo'] };
  const item = { ...role(1, 'Gastronomia'), caracteristicas: attributes(['Rock'], ['Japonesa'], ['Tranquilo']) };
  const reasons = preferenceReasons(item, selected, '2026-10-20');
  assert.equal(reasons.length, 3);
  assert.ok(reasons.every(r => r.includes('informado pelo responsável')));
  assert.match(reasons.join(' '), /Rock.*Japonesa.*Tranquilo/);
  assert.deepEqual(preferenceReasons({ ...role(2, 'Música'), caracteristicas: attributes(['Sertanejo']) }, selected, '2026-10-20'), []);
  assert.deepEqual(preferenceReasons({ ...role(3, 'Gastronomia'), caracteristicas: attributes([], ['Italiana']) }, selected, '2026-10-20'), []);
  assert.deepEqual(preferenceReasons(item, { ...selected, status: 'PULADO' }, '2026-10-20'), []);
});
test('exact matches rank within schedule tiers, then event date and distance; no mutation or duplicates', () => {
  const selected = { ...preferences, musica: ['Rock'] };
  const base = { categoria: 'Música', data: '2026-10-20', horario: '10:00', horarioFim: '20:00', distanciaKm: 0.1 };
  const matching = { ...base, id: 2, distanciaKm: 3, caracteristicas: attributes(['Rock']) };
  const later = { ...matching, id: 3, horario: '18:00' };
  const items = [{ ...base, id: 1 }, matching, matching, later];
  const before = JSON.stringify(items);
  assert.deepEqual(nearbyHighlights(items, selected, '2026-10-20', '12:00').map(v => v.role.id), [2, 1, 3]);
  assert.equal(JSON.stringify(items), before);
  assert.deepEqual(nearbyHighlights([{ ...matching, cancelado: true }, { ...matching, id: 4, distanciaKm: 6 }, { ...matching, id: 5, horarioFim: '11:00' }], selected, '2026-10-20', '12:00'), []);
});
test('empty metadata keeps explicit fallback and known nonmatching ambience is not inferred', () => {
  assert.match(preferenceReasons({ ...role(1, 'Música'), caracteristicas: attributes() }, { ...preferences, musica: ['Rock'] }, '2026-10-20')[0], /gênero não foi informado/);
  assert.deepEqual(preferenceReasons({ ...role(2, 'Cultura'), caracteristicas: attributes([], [], ['Animado']) }, { ...preferences, ambientes: ['Cultural'] }, '2026-10-20'), []);
});

test('expanded activities match their categories without claiming confirmed availability', () => {
  for (const [interest, category] of [['História e patrimônio', 'História'], ['Ciência', 'Ciência'], ['Trilhas', 'Natureza'], ['Jogos', 'Jogos'], ['Turismo religioso', 'Turismo religioso']]) {
    const result = preferenceReasons(role(1, category), { ...preferences, atividades: [interest] }, '2026-11-26');
    assert.equal(result.length, 1);
    assert.match(result[0], /se relaciona à categoria/);
  }
});
