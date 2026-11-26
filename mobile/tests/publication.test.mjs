import test from 'node:test';
import assert from 'node:assert/strict';
import { publicationInput, publicationError, publicationVersion } from '../src/publication.ts';
const draft = { id: null, titulo: ' Meu passeio ', relato: ' Relato ', publico: false, versao: 0, cidade: 'Uberaba', paradas: [], fotos: [{ base64: 'abc', legenda: ' Foto ' }] };
test('publication payload only contains explicit share fields, not private planning or source identity', () => {
  const payload = publicationInput({ ...draft, contaId: 7, data: '2026-11-26', pessoas: 4, orcamentoCentavos: 999, latitude: -20 }, true);
  assert.deepEqual(payload, { id: null, titulo: 'Meu passeio', relato: 'Relato', publico: true, versao: 0, fotos: [{ base64: 'abc', legenda: 'Foto' }] });
  assert.equal(draft.publico, false);
});
test('publication validates title story caption photo count and upload size', () => {
  assert.equal(publicationError(draft), '');
  for (const invalid of [{ titulo: ' ' }, { titulo: 'a'.repeat(121) }, { relato: 'a'.repeat(3001) }, { fotos: Array(4).fill(draft.fotos[0]) }, { fotos: [{ base64: 'A'.repeat(2796205), legenda: '' }] }, { fotos: [{ base64: 'abc', legenda: 'a'.repeat(201) }] }]) assert.ok(publicationError({ ...draft, ...invalid }));
  assert.equal(publicationError({ ...draft, fotos: [] }), '');
});
test('hide and deletion carry saved version and identity without unsaved edits or images', () => {
  assert.deepEqual(publicationVersion({ ...draft, id: 'saved-id', versao: 4 }), { id: 'saved-id', versao: 4 });
});
