-- FICTIONAL fixtures, exclusively for compose.integration.yml.
-- Coordinates are illustrative points in Uberaba, not verified businesses.
-- Never install this as a production migration. Existing records are not changed.
START TRANSACTION;
INSERT INTO organizacoes (nome, tipo, descricao, contato_email, status)
SELECT 'Teste - Catalogo do mapa', 'OUTRO', 'Dados ficticios para teste local. Nenhuma oferta real.',
       'map-demo-v1@example.invalid', 'APROVADA'
WHERE NOT EXISTS (SELECT 1 FROM organizacoes WHERE contato_email='map-demo-v1@example.invalid');
SET @demo_org = (SELECT id FROM organizacoes WHERE contato_email='map-demo-v1@example.invalid' ORDER BY id LIMIT 1);

CREATE TEMPORARY TABLE demo_map_points (
    local_nome VARCHAR(200), nome VARCHAR(200), categoria VARCHAR(100),
    latitude DECIMAL(9,6), longitude DECIMAL(9,6), valor DECIMAL(10,2)
);
INSERT INTO demo_map_points VALUES
('Teste - Espaco cultural', 'Teste - Exposicao de arte', 'Cultura', -19.750001, -47.930001, 0),
('Teste - Espaco cultural', 'Teste - Oficina de pintura', 'Cultura', -19.750001, -47.930001, 25),
('Teste - Cafe ficticio', 'Teste - Encontro no cafe', 'Gastronomia', -19.758000, -47.946000, 20),
('Teste - Palco ficticio', 'Teste - Musica ao vivo', 'Música', -19.743000, -47.942000, 35),
('Teste - Area de lazer', 'Teste - Passeio ao ar livre', 'Lazer', -19.765000, -47.937000, 0),
('Teste - Feira ficticia', 'Teste - Feira de sabores', 'Gastronomia', -19.754000, -47.957000, 15);

INSERT INTO locais (organizacao_id, nome, descricao, categoria, endereco, cidade, uf, latitude, longitude, status)
SELECT DISTINCT @demo_org, d.local_nome, 'Local ficticio. Coordenadas ilustrativas para testar o mapa.',
       d.categoria, 'Endereco ficticio - nao se deslocar ate este ponto', 'Uberaba', 'MG', d.latitude, d.longitude, 'PUBLICADO'
FROM demo_map_points d
WHERE NOT EXISTS (SELECT 1 FROM locais l WHERE l.organizacao_id=@demo_org AND l.nome=d.local_nome);

INSERT INTO roles (nome, descricao, local, cidade, categoria, valor, local_id)
SELECT d.nome, 'EXEMPLO FICTICIO: atividade e preco apenas para testes. Nao e um evento real e nao possui reserva ou disponibilidade.',
       l.nome, l.cidade, d.categoria, d.valor, l.id
FROM demo_map_points d JOIN locais l ON l.organizacao_id=@demo_org AND l.nome=d.local_nome
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.local_id=l.id AND r.nome=d.nome);
COMMIT;
SELECT COUNT(*) AS demo_roles, COUNT(DISTINCT r.local_id) AS demo_locations
FROM roles r JOIN locais l ON l.id=r.local_id WHERE l.organizacao_id=@demo_org;
