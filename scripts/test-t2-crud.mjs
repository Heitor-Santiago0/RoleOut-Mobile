import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Local integration environment only. Never point this fixture at a production database.
const cwd = fileURLToPath(new URL('..', import.meta.url));
const base = 'http://127.0.0.1:8082/RoleOut/api/v1';
const run = randomUUID();
const marker = `T2-CRUD-${run}`;
const emails = [0, 1].map(i => `t2-${run}-${i}@example.com`);
const docker = process.env.DOCKER_BIN || 'docker';
function sql(query) {
  return execFileSync(docker, ['compose', '-f', 'compose.integration.yml', 'exec', '-T', 'mysql',
    'mysql', '-uroleout', '-pintegration-local-only', '--batch', '--skip-column-names', 'roleout'],
    { cwd, input: query, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
}
async function call(path, token, method = 'GET', body, status = 200) {
  const response = await fetch(base + path, {
    method, signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  assert.equal(response.status, status, `${method} ${path}: expected ${status}, received ${response.status}`);
  return status === 204 ? null : response.json();
}
try {
  await call('/roteiros', null, 'GET', undefined, 401);
  const roleId = Number(sql(`INSERT INTO roles(nome,cidade,local,valor,categoria) VALUES ('${marker}','Cidade T2','Local ficticio T2',10.00,'Cultura'); SELECT LAST_INSERT_ID();`));
  assert.ok(Number.isSafeInteger(roleId) && roleId > 0);
  const tokens = [];
  for (const email of emails) {
    const senha = randomUUID() + 'A1!';
    await call('/auth/cadastro', null, 'POST', { nome: 'Teste temporário T2', email, senha }, 201);
    tokens.push((await call('/auth/login', null, 'POST', { email, senha })).token);
  }
  const [owner, other] = tokens;
  const input = { nome: marker, cidade: 'Cidade T2', data: '2026-11-26', partida: '14:00',
    duracaoMinutos: 180, pessoas: 1, orcamentoCentavos: 10000, modoOrcamento: 'group',
    paradas: [{ roleId, minutos: 60 }] };
  const saved = await call('/roteiros', owner, 'POST', input, 201);
  const id = saved.roteiro.id;
  assert.equal(saved.paradas.length, 1);
  assert.equal(saved.roteiro.totalCentavos, 1000);
  assert.equal((await call(`/roteiros/${id}`, owner)).roteiro.nome, marker);
  assert.ok((await call('/roteiros', owner)).itens.some(item => item.id === id));
  for (const [method, path, body] of [
    ['GET', `/roteiros/${id}`], ['PUT', `/roteiros/${id}/nome`, { nome: 'Indevido' }], ['DELETE', `/roteiros/${id}`],
  ]) await call(path, other, method, body, 404);
  await call(`/roteiros/${id}/nome`, owner, 'PUT', { nome: ' ' }, 400);
  const renamed = await call(`/roteiros/${id}/nome`, owner, 'PUT', { nome: 'Passeio T2 atualizado' });
  assert.equal(renamed.roteiro.nome, 'Passeio T2 atualizado');
  assert.equal((await call(`/roteiros/${id}`, owner)).roteiro.nome, renamed.roteiro.nome);
  const progress = await call(`/roteiros/${id}/paradas/${roleId}`, owner, 'PUT', { concluida: true });
  assert.equal(progress.paradas[0].concluida, true);
  await call('/roteiros', owner, 'POST', { ...input, orcamentoCentavos: 0 }, 409);
  assert.equal((await call('/roteiros', owner)).itens.length, 1, 'Rejected creation must not leave partial data');
  await call(`/roteiros/${id}`, owner, 'DELETE', undefined, 204);
  await call(`/roteiros/${id}`, owner, 'GET', undefined, 404);
  assert.equal((await call('/roteiros', owner)).itens.length, 0);
  console.log('T2 HTTP/MySQL: CRUD, persistence, account isolation, validation, progress and rollback passed.');
} finally {
  // Exact random identifiers generated above; no existing account or catalog record is targeted.
  const selected = emails.map(email => `'${email}'`).join(',');
  sql(`START TRANSACTION;
    DELETE FROM roteiros WHERE conta_id IN (SELECT id FROM contas WHERE email IN (${selected}));
    DELETE FROM sessoes WHERE conta_id IN (SELECT id FROM contas WHERE email IN (${selected}));
    DELETE FROM contas WHERE email IN (${selected});
    DELETE FROM roles WHERE nome='${marker}' AND cidade='Cidade T2' AND local_id IS NULL;
    COMMIT;`);
  console.log('Temporary T2 fixtures removed.');
}
