-- Existing saved stops start pending; historical snapshots remain intact.
ALTER TABLE roteiro_paradas ADD COLUMN concluida BOOLEAN NOT NULL DEFAULT FALSE;
