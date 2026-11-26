CREATE TABLE comunidade_textos (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 conta_id INT NOT NULL,
 tipo VARCHAR(16) NOT NULL,
 alvo VARCHAR(36) NOT NULL,
 texto VARCHAR(1000) NOT NULL,
 nota INT NULL,
 oculto BOOLEAN NOT NULL DEFAULT FALSE,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE,
 UNIQUE KEY uk_texto_autor (conta_id,tipo,alvo),
 INDEX ix_textos_alvo (tipo,alvo,id)
);
CREATE TABLE comunidade_seguidos (
 conta_id INT NOT NULL,
 perfil_id VARCHAR(36) NOT NULL,
 PRIMARY KEY (conta_id,perfil_id),
 FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE
);
CREATE TABLE comunidade_denuncias (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 conta_id INT NOT NULL,
 tipo VARCHAR(16) NOT NULL,
 alvo VARCHAR(36) NOT NULL,
 motivo VARCHAR(1000) NOT NULL,
 estado VARCHAR(16) NOT NULL DEFAULT 'PENDENTE',
 decisao VARCHAR(1000) NULL,
 revisor_id INT NULL,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE,
 UNIQUE KEY uk_denuncia (conta_id,tipo,alvo)
);
CREATE TABLE comunidade_bloqueios (
 tipo VARCHAR(16) NOT NULL,
 alvo VARCHAR(36) NOT NULL,
 PRIMARY KEY (tipo,alvo)
);
CREATE TABLE roteiro_creditos (
 roteiro_id INT PRIMARY KEY,
 publicacao_id VARCHAR(36) NOT NULL,
 autor_id VARCHAR(36) NOT NULL,
 autor_nome VARCHAR(80) NOT NULL,
 titulo VARCHAR(120) NOT NULL,
 FOREIGN KEY (roteiro_id) REFERENCES roteiros(id) ON DELETE CASCADE
);
