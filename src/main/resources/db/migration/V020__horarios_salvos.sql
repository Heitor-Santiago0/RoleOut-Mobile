-- NULL means the original published schedule was not captured; never infer history.
ALTER TABLE roteiro_paradas ADD COLUMN horarios_originais TEXT NULL;
