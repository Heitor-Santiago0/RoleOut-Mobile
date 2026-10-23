CREATE TABLE sugestoes_config (
    conta_id INT NOT NULL PRIMARY KEY,
    ativo BOOLEAN NOT NULL DEFAULT FALSE,
    viagens BOOLEAN NOT NULL DEFAULT FALSE,
    intervalo_horas INT NOT NULL DEFAULT 24,
    cidade_base VARCHAR(100) NULL,
    ultimo_aviso TIMESTAMP NULL,
    CONSTRAINT fk_sugestoes_conta FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE
);
