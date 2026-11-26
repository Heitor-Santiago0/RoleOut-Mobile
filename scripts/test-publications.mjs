import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const cwd = fileURLToPath(new URL('..', import.meta.url));
const base = 'http://127.0.0.1:8082/RoleOut/api/v1';
const id = randomUUID(), marker = `publication-${id}`;
const emails = [0, 1].map(i => `${marker}-${i}@example.com`);
function sql(query) {
  return execFileSync(process.env.DOCKER_BIN || 'docker', ['compose', '-f', 'compose.integration.yml', 'exec', '-T', 'mysql', 'mysql', '-uroleout', '-pintegration-local-only', '--batch', '--skip-column-names', 'roleout'], { cwd, input: query, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
}
async function call(path, token, method = 'GET', body, status = 200) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  assert.equal(response.status, status, `${method} ${path}: expected ${status}, got ${response.status}`);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  return status === 204 ? null : response.json();
}
const photo = { base64: 'iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAFElEQVR4nGNkYPjPgA0wYRUdtBIAy0MBD1YkjLoAAAAASUVORK5CYII=', legenda: 'Foto de teste' };
try {
  await call('/publicacoes?autor=' + id, null, 'GET', undefined, 401);
  const role = Number(sql(`INSERT INTO roles(nome,cidade,local,valor,categoria) VALUES ('${marker}','Cidade Teste','Local ficticio',0,'Cultura'); SELECT LAST_INSERT_ID();`));
  assert.ok(Number.isSafeInteger(role) && role > 0);
  const tokens = [];
  for (const email of emails) {
    const senha = randomUUID() + 'A1!';
    await call('/auth/cadastro', null, 'POST', { nome: 'Conta privada de teste', email, senha }, 201);
    tokens.push((await call('/auth/login', null, 'POST', { email, senha })).token);
  }
  const [a, b] = tokens;
  const route = await call('/roteiros', a, 'POST', { nome: 'Roteiro privado', cidade: 'Cidade Teste', data: '2026-11-26', partida: '14:00', duracaoMinutos: 180, pessoas: 2, orcamentoCentavos: 10000, modoOrcamento: 'group', paradas: [{ roleId: role, minutos: 60 }] }, 201);
  const ownPath = `/minhas-publicacoes/${route.roteiro.id}`;
  const initial = await call(ownPath, a);
  assert.equal(initial.id, null); assert.equal(initial.publico, false);
  await call(ownPath, b, 'GET', undefined, 404);
  const payload = { id: null, titulo: 'Passeio compartilhado', relato: 'Meu relato de teste', publico: false, versao: 0, fotos: [photo] };
  await call(ownPath, a, 'PUT', { ...payload, publico: true }, 409);
  const draft = await call(ownPath, a, 'PUT', payload);
  const profile = await call('/perfil-social', a, 'PUT', { nome: 'Autor público', apresentacao: '', publico: true, versao: 0 });
  await call(`/publicacoes/${draft.id}`, b, 'GET', undefined, 404);
  const published = await call(ownPath, a, 'PUT', { ...payload, id: draft.id, versao: draft.versao, publico: true });
  const view = await call(`/publicacoes/${published.id}`, b);
  assert.deepEqual(Object.keys(view).sort(), ['id', 'autor', 'titulo', 'relato', 'cidade', 'paradas', 'fotos'].sort());
  assert.equal(view.autor.nome, 'Autor público');assert.equal(view.fotos.length, 1);
  assert.equal(Buffer.from(view.fotos[0].base64, 'base64').subarray(0, 2).toString('hex'), 'ffd8');
  assert.equal(JSON.stringify(view).includes('2026-11-26'), false);
  assert.equal((await call(`/publicacoes?autor=${profile.id}`, b)).itens.length, 1);
  await call(ownPath, a, 'PUT', { ...payload, id: published.id, versao: published.versao, fotos: [{ base64: 'bad', legenda: '' }] }, 400);
  assert.equal((await call(ownPath, a)).versao, published.versao);
  assert.deepEqual(await call(`/roteiros/${route.roteiro.id}`, a), route);
  await call('/perfil-social', a, 'PUT', { nome: profile.nome, apresentacao: '', publico: false, versao: profile.versao });
  await call(`/publicacoes/${published.id}`, b, 'GET', undefined, 404);
  await call('/perfil-social', a, 'PUT', { nome: profile.nome, apresentacao: '', publico: true, versao: profile.versao + 1 });
  const hidden = await call(`${ownPath}/ocultar`, a, 'PUT', { id: published.id, versao: published.versao });
  await call(`/publicacoes/${published.id}`, b, 'GET', undefined, 404);
  await call(ownPath, a, 'PUT', { ...payload, id: published.id, versao: published.versao, publico: true }, 409);
  await call(ownPath, a, 'DELETE', { id: hidden.id, versao: hidden.versao }, 204);
  assert.equal((await call(ownPath, a)).id, null);
  assert.deepEqual(await call(`/roteiros/${route.roteiro.id}`, a), route);
  console.log('HTTP/MySQL publication: private draft, photos, explicit publish, second-account viewing, privacy, hide, conflict and delete passed.');
} finally {
  const selected = emails.map(email => `'${email}'`).join(',');
  sql(`START TRANSACTION;
    DELETE FROM roteiros WHERE conta_id IN (SELECT id FROM contas WHERE email IN (${selected}));
    DELETE FROM sessoes WHERE conta_id IN (SELECT id FROM contas WHERE email IN (${selected}));
    DELETE FROM contas WHERE email IN (${selected});
    DELETE FROM roles WHERE nome='${marker}' AND cidade='Cidade Teste' AND local_id IS NULL;
    COMMIT;`);
  console.log('Temporary publication fixtures removed.');
}
