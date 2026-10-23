-- Keep existing jobs intact; NULL denotes a legacy job without stop-level history.
ALTER TABLE push_envios ADD COLUMN roles_afetados TEXT NULL;
