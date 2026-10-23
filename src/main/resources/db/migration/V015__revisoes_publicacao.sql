ALTER TABLE rascunhos_roles ADD COLUMN cancelado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rascunhos_roles ADD COLUMN motivo_cancelamento VARCHAR(2000) NULL;
-- Older decisions have no reconstructable snapshot. Preserve them as unknown.
ALTER TABLE revisoes_roles ADD COLUMN conteudo TEXT NULL;
