-- Opt-in per authenticated device/session. Logout removes subscriptions and queued work.
CREATE TABLE push_dispositivos (
 id INT AUTO_INCREMENT PRIMARY KEY,
 sessao_hash CHAR(64) NOT NULL UNIQUE,
 expo_token VARCHAR(255) NOT NULL UNIQUE,
 CONSTRAINT fk_push_sessao FOREIGN KEY (sessao_hash) REFERENCES sessoes(token_hash) ON DELETE CASCADE
);
CREATE TABLE push_estado (
 dispositivo_id INT NOT NULL,
 roteiro_id INT NOT NULL,
 observado TEXT NOT NULL,
 PRIMARY KEY(dispositivo_id,roteiro_id),
 FOREIGN KEY(dispositivo_id) REFERENCES push_dispositivos(id) ON DELETE CASCADE,
 FOREIGN KEY(roteiro_id) REFERENCES roteiros(id) ON DELETE CASCADE
);
CREATE TABLE push_envios (
 id INT AUTO_INCREMENT PRIMARY KEY,
 dispositivo_id INT NOT NULL,
 roteiro_id INT NOT NULL,
 estado VARCHAR(24) NOT NULL DEFAULT 'PENDENTE',
 tentativas INT NOT NULL DEFAULT 0,
 proxima_tentativa TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ticket VARCHAR(200),
 erro VARCHAR(80),
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(dispositivo_id) REFERENCES push_dispositivos(id) ON DELETE CASCADE,
 FOREIGN KEY(roteiro_id) REFERENCES roteiros(id) ON DELETE CASCADE
);
CREATE INDEX ix_push_fila ON push_envios(estado,proxima_tentativa);
