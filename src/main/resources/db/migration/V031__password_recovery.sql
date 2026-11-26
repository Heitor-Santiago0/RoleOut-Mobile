-- Additive recovery queue. Never store the plaintext recovery code.
CREATE TABLE recuperacoes_senha (
 conta_id INT NOT NULL PRIMARY KEY,
 solicitado_em TIMESTAMP NOT NULL,
 janela_inicio TIMESTAMP NOT NULL,
 solicitacoes INT NOT NULL,
 pendente BOOLEAN NOT NULL,
 tentativas INT NOT NULL DEFAULT 0,
 proxima_tentativa TIMESTAMP NOT NULL,
 token_hash CHAR(64) NULL UNIQUE,
 expira_em TIMESTAMP NULL,
 FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE
);
CREATE INDEX ix_recuperacoes_fila ON recuperacoes_senha(pendente,proxima_tentativa);
