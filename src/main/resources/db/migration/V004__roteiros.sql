CREATE TABLE roteiros (
    id INT AUTO_INCREMENT PRIMARY KEY,
    conta_id INT NOT NULL,
    nome VARCHAR(200) NOT NULL,
    cidade VARCHAR(150) NOT NULL,
    data_planejada DATE NOT NULL,
    partida CHAR(5) NOT NULL,
    duracao_minutos INT NOT NULL,
    pessoas INT NOT NULL,
    orcamento_centavos BIGINT NOT NULL,
    modo_orcamento VARCHAR(10) NOT NULL,
    total_centavos BIGINT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_roteiros_conta FOREIGN KEY (conta_id) REFERENCES contas(id),
    CONSTRAINT ck_roteiros_pessoas CHECK (pessoas BETWEEN 1 AND 50),
    CONSTRAINT ck_roteiros_duracao CHECK (duracao_minutos BETWEEN 60 AND 720),
    CONSTRAINT ck_roteiros_orcamento CHECK (orcamento_centavos >= 0 AND total_centavos >= 0 AND total_centavos <= orcamento_centavos),
    CONSTRAINT ck_roteiros_modo CHECK (modo_orcamento IN ('person','group'))
);
CREATE INDEX ix_roteiros_conta ON roteiros(conta_id, id);

-- Historical snapshots deliberately do not reference roles: legacy web deletion remains functional.
CREATE TABLE roteiro_paradas (
    roteiro_id INT NOT NULL,
    ordem INT NOT NULL,
    role_id INT NOT NULL,
    nome VARCHAR(255) NOT NULL,
    local_nome VARCHAR(255),
    minutos INT NOT NULL,
    preco_centavos BIGINT NOT NULL,
    PRIMARY KEY (roteiro_id, ordem),
    CONSTRAINT uq_roteiro_role UNIQUE (roteiro_id, role_id),
    CONSTRAINT fk_paradas_roteiro FOREIGN KEY (roteiro_id) REFERENCES roteiros(id) ON DELETE CASCADE,
    CONSTRAINT ck_paradas_minutos CHECK (minutos BETWEEN 15 AND 720),
    CONSTRAINT ck_paradas_preco CHECK (preco_centavos >= 0)
);
