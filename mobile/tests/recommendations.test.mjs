import test from 'node:test';
import assert from 'node:assert/strict';
import { preferenceReasons, preferenceHighlights } from '../src/recommendations.ts';
const preferences = { status: 'CONCLUIDO', musica: [], comida: [], atividades: [], ambientes: [] };
const role = (id, categoria, data = null) => ({ id, categoria, data });
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
