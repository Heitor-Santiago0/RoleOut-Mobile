-- Administrator rights are provisioned outside the public signup API.
ALTER TABLE contas ADD COLUMN administrador BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE sessoes (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    conta_id INT NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    CONSTRAINT fk_sessoes_conta FOREIGN KEY (conta_id) REFERENCES contas (id)
);
CREATE INDEX ix_sessoes_expiracao ON sessoes (expira_em);
