-- Private authoring area: never queried by the legacy web or public Explore.
CREATE TABLE rascunhos_roles (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    local_id INT NOT NULL,
    nome VARCHAR(200) NOT NULL,
    descricao TEXT,
    categoria VARCHAR(100) NOT NULL,
    data DATE,
    valor DECIMAL(10,2) NOT NULL,
    versao INT NOT NULL DEFAULT 1,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rascunhos_local FOREIGN KEY (local_id) REFERENCES locais(id),
    CONSTRAINT ck_rascunhos_valor CHECK (valor >= 0)
);
CREATE INDEX ix_rascunhos_local ON rascunhos_roles(local_id, id);
