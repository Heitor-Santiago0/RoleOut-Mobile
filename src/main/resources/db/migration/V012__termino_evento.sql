-- Optional: existing events retain unknown end times. Never infer closing hours.
ALTER TABLE roles ADD COLUMN horario_fim VARCHAR(5) NULL;
