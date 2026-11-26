import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const cwd = fileURLToPath(new URL('..', import.meta.url));
const base = 'http://127.0.0.1:8082/RoleOut/api/v1';
const id = randomUUID(), marker = `community-${id}`;
const emails = [0, 1, 2].map(i => `${marker}-${i}@example.com`);
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
let publication;
try {
  await call('/comunidade/feed', null, 'GET', undefined, 401);
  const role = Number(sql(`INSERT INTO roles(nome,cidade,local,valor,categoria) VALUES ('${marker}','Cidade Teste','Local ficticio',10,'Cultura'); SELECT LAST_INSERT_ID();`));
  const tokens = [];
  for (const email of emails) {
    const senha = randomUUID() + 'A1!';
    await call('/auth/cadastro', null, 'POST', { nome: 'Teste temporario', email, senha }, 201);
    tokens.push((await call('/auth/login', null, 'POST', { email, senha })).token);
  }
  const [a,b,admin] = tokens;
  sql(`UPDATE contas SET administrador=TRUE WHERE email='${emails[2]}'`);
  const profile = await call('/perfil-social', a, 'PUT', { nome: 'Autor comunidade', apresentacao: '', publico: true, versao: 0 });
  await call('/perfil-social', b, 'PUT', { nome: 'Visitante comunidade', apresentacao: '', publico: true, versao: 0 });
  const plan = { nome: 'Original', cidade: 'Cidade Teste', data: '2026-11-26', partida: '10:00', duracaoMinutos: 180, pessoas: 1, orcamentoCentavos: 10000, modoOrcamento: 'group', paradas: [{ roleId: role, minutos: 60 }] };
  const route = await call('/roteiros', a, 'POST', plan, 201);
  publication = (await call(`/minhas-publicacoes/${route.roteiro.id}`, a, 'PUT', { id: null, titulo: 'Passeio comunidade', relato: 'Relato publico', publico: true, versao: 0, fotos: [] })).id;
  assert.ok((await call('/comunidade/feed', b)).itens.some(p => p.id === publication));
  await call(`/comunidade/seguir/${profile.id}`, b, 'PUT', undefined, 204);
  assert.ok((await call('/comunidade/feed?seguindo=true', b)).itens.some(p => p.id === publication));
  const comments = `/comunidade/textos/COMENTARIO/${publication}`;
  await call(comments, b, 'PUT', { texto: 'Gostei!', nota: null }, 204);
  await call(comments, b, 'PUT', { texto: 'Gostei muito!', nota: null }, 204);
  assert.equal((await call(comments,a)).total, 1);
  await call(comments,a,'DELETE',undefined,204);
  assert.equal((await call(comments,b)).total, 1);
  const copy = await call(`/comunidade/copiar/${publication}`, b, 'POST', { planejamento: {...plan, nome: 'Minha copia', data: '2001-01-01'} }, 201);
  assert.equal((await call(`/comunidade/creditos/${copy.roteiro.id}`, b)).creditos[0].autorId, profile.id);
  await call(`/comunidade/creditos/${copy.roteiro.id}`, a,'GET',undefined,404);
  assert.deepEqual(await call(`/roteiros/${route.roteiro.id}`, a),route);
  const ratings = `/comunidade/textos/AVALIACAO/${role}`;
  await call(ratings,b,'PUT',{texto:'Bom',nota:5},409);
  await call(`/roteiros/${copy.roteiro.id}/paradas/${role}`,b,'PUT',{concluida:true});
  await call(ratings,b,'PUT',{texto:'Bom',nota:5},204);
  assert.equal((await call(ratings,a)).media,5);
  sql(`UPDATE roles SET valor=1000 WHERE id=${role}`);
  await call(`/comunidade/copiar/${publication}`, b, 'POST', { planejamento: plan },409);
  sql(`UPDATE roles SET cancelado=TRUE WHERE id=${role}`);
  await call(`/comunidade/copiar/${publication}`, b, 'POST', { planejamento: plan },409);
  await call('/comunidade/denuncias',b,'POST',{tipo:'PUBLICACAO',alvo:publication,motivo:'Teste de moderacao'},204);
  await call('/comunidade/denuncias',b,'GET',undefined,403);
  const report=(await call('/comunidade/denuncias',admin)).itens.find(d=>d.alvo===publication);
  assert.ok(report);await call(`/comunidade/denuncias/${report.id}`,admin,'PUT',{ocultar:true,justificativa:'Teste confirmado'},204);
  await call(`/publicacoes/${publication}`,b,'GET',undefined,404);
  assert.ok(!(await call('/comunidade/feed',b)).itens.some(p=>p.id===publication));
  await call(comments,b,'GET',undefined,404);
  await call(`/comunidade/copiar/${publication}`, b,'POST',{planejamento:plan},404);
  console.log('HTTP/MySQL community: feed, following, comments, private copy, author credits, new prices, declared visits, ratings, report and moderation passed.');
} finally {
  const selected=emails.map(email=>`'${email}'`).join(',');
  sql(`START TRANSACTION;
    DELETE FROM comunidade_bloqueios WHERE tipo='PUBLICACAO' AND alvo IN (SELECT id_publico FROM publicacoes_roteiros WHERE conta_id IN (SELECT id FROM contas WHERE email IN (${selected})));
    DELETE FROM roteiros WHERE conta_id IN (SELECT id FROM contas WHERE email IN (${selected}));
    DELETE FROM sessoes WHERE conta_id IN (SELECT id FROM contas WHERE email IN (${selected}));
    DELETE FROM contas WHERE email IN (${selected});
    DELETE FROM roles WHERE nome='${marker}' AND cidade='Cidade Teste' AND local_id IS NULL;
    COMMIT;`);
  console.log('Temporary community fixtures removed.');
}
