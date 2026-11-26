-- Explicit opt-in snapshots. No existing itinerary is published by this migration.
CREATE TABLE publicacoes_roteiros (
    roteiro_id INT NOT NULL PRIMARY KEY,
    id_publico CHAR(36) NOT NULL UNIQUE,
    conta_id INT NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    relato VARCHAR(3000) NOT NULL,
    cidade VARCHAR(150) NOT NULL,
    paradas TEXT NOT NULL,
    publico BOOLEAN NOT NULL DEFAULT FALSE,
    versao INT NOT NULL,
    CONSTRAINT fk_publicacoes_roteiro FOREIGN KEY (roteiro_id) REFERENCES roteiros(id) ON DELETE CASCADE,
    CONSTRAINT fk_publicacoes_conta FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE
);
CREATE INDEX ix_publicacoes_autor ON publicacoes_roteiros(conta_id, publico, id_publico);
CREATE TABLE publicacoes_fotos (
    roteiro_id INT NOT NULL,
    ordem INT NOT NULL,
    legenda VARCHAR(200) NOT NULL,
    imagem MEDIUMBLOB NOT NULL,
    PRIMARY KEY (roteiro_id, ordem),
    CONSTRAINT fk_publicacoes_fotos FOREIGN KEY (roteiro_id) REFERENCES publicacoes_roteiros(roteiro_id) ON DELETE CASCADE,
    CONSTRAINT ck_publicacoes_foto_ordem CHECK (ordem BETWEEN 0 AND 2)
);
