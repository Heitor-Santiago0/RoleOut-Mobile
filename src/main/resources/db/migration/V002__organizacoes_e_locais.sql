-- Foundation only: authentication, authorization and approval flows follow later.
CREATE TABLE contas (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    email_confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE organizacoes (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    descricao TEXT,
    contato_email VARCHAR(254) NOT NULL,
    contato_telefone VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_organizacoes_tipo CHECK (tipo IN
        ('RESTAURANTE', 'BAR', 'MUSEU', 'PREFEITURA', 'OUTRO')),
    CONSTRAINT ck_organizacoes_status CHECK (status IN
        ('PENDENTE', 'APROVADA', 'REJEITADA', 'SUSPENSA'))
);

CREATE TABLE membros_organizacao (
    organizacao_id INT NOT NULL,
    conta_id INT NOT NULL,
    papel VARCHAR(20) NOT NULL,
    PRIMARY KEY (organizacao_id, conta_id),
    CONSTRAINT fk_membros_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    CONSTRAINT fk_membros_conta FOREIGN KEY (conta_id) REFERENCES contas (id),
    CONSTRAINT ck_membros_papel CHECK (papel IN ('RESPONSAVEL', 'EDITOR'))
);

CREATE TABLE locais (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organizacao_id INT NOT NULL,
    nome VARCHAR(200) NOT NULL,
    descricao TEXT,
    categoria VARCHAR(100) NOT NULL,
    endereco VARCHAR(255) NOT NULL,
    cidade VARCHAR(150) NOT NULL,
    uf CHAR(2) NOT NULL,
    cep VARCHAR(9),
    latitude DECIMAL(9, 6),
    longitude DECIMAL(9, 6),
    acessibilidade TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_locais_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    CONSTRAINT ck_locais_status CHECK (status IN
        ('RASCUNHO', 'PENDENTE', 'PUBLICADO', 'REJEITADO', 'SUSPENSO')),
    CONSTRAINT ck_locais_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_locais_longitude CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_locais_explorar ON locais (status, cidade, categoria);

CREATE TABLE revisoes_cadastro (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organizacao_id INT NOT NULL,
    local_id INT,
    revisor_conta_id INT NOT NULL,
    decisao VARCHAR(20) NOT NULL,
    justificativa TEXT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_revisoes_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    CONSTRAINT fk_revisoes_local FOREIGN KEY (local_id) REFERENCES locais (id),
    CONSTRAINT fk_revisoes_revisor FOREIGN KEY (revisor_conta_id) REFERENCES contas (id),
    CONSTRAINT ck_revisoes_decisao CHECK (decisao IN ('APROVAR', 'REJEITAR', 'SUSPENDER'))
);

-- Optional association preserves the original web form and its textual local.
ALTER TABLE roles ADD COLUMN local_id INT;
ALTER TABLE roles ADD CONSTRAINT fk_roles_local FOREIGN KEY (local_id) REFERENCES locais (id);
