CREATE TABLE sugestoes_entregas (
    conta_id INT NOT NULL,
    role_id INT NOT NULL,
    enviado_em TIMESTAMP NOT NULL,
    PRIMARY KEY (conta_id, role_id),
    CONSTRAINT fk_sugestoes_entregas_conta FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE
);
CREATE INDEX idx_sugestoes_entregas_data ON sugestoes_entregas (enviado_em);
ALTER TABLE sugestoes_config ALTER COLUMN intervalo_horas SET DEFAULT 0;
-- Existing explicit settings remain unchanged. 0 selects the daily policy.
