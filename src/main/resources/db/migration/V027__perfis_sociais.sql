-- Opt-in only. Existing accounts and private itineraries remain untouched.
CREATE TABLE perfis_sociais (
    conta_id INT NOT NULL PRIMARY KEY,
    id_publico CHAR(36) NOT NULL UNIQUE,
    nome VARCHAR(80) NOT NULL,
    apresentacao VARCHAR(500) NOT NULL,
    publico BOOLEAN NOT NULL DEFAULT FALSE,
    versao INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_perfis_sociais_conta FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE
);
CREATE INDEX ix_perfis_sociais_publicos ON perfis_sociais(publico, id_publico);
