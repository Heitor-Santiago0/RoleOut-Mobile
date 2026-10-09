ALTER TABLE rascunhos_roles ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO';
ALTER TABLE rascunhos_roles ADD COLUMN role_id INT;
ALTER TABLE rascunhos_roles ADD CONSTRAINT ck_rascunhos_status CHECK (status IN ('RASCUNHO','PENDENTE','REJEITADO','PUBLICADO'));
ALTER TABLE rascunhos_roles ADD CONSTRAINT fk_rascunhos_publicacao FOREIGN KEY (role_id) REFERENCES roles(id);
ALTER TABLE rascunhos_roles ADD CONSTRAINT uq_rascunhos_publicacao UNIQUE (role_id);
CREATE INDEX ix_rascunhos_revisao ON rascunhos_roles(status, id);
CREATE TABLE revisoes_roles (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rascunho_id INT NOT NULL,
    versao INT NOT NULL,
    revisor_conta_id INT NOT NULL,
    decisao VARCHAR(20) NOT NULL,
    justificativa TEXT NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_revisoes_role_rascunho FOREIGN KEY (rascunho_id) REFERENCES rascunhos_roles(id),
    CONSTRAINT fk_revisoes_role_conta FOREIGN KEY (revisor_conta_id) REFERENCES contas(id),
    CONSTRAINT uq_revisoes_role_versao UNIQUE (rascunho_id, versao),
    CONSTRAINT ck_revisoes_role_decisao CHECK (decisao IN ('APROVAR','REJEITAR'))
);
