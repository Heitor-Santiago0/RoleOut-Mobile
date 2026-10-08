-- Baseline for a NEW database, inferred from the existing web DAO.
-- Do not apply to an existing roles table without comparing SHOW CREATE TABLE.
CREATE TABLE roles (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    descricao TEXT,
    local VARCHAR(255),
    cidade VARCHAR(150) NOT NULL,
    data DATE,
    categoria VARCHAR(100),
    valor DECIMAL(10, 2) NOT NULL,
    CONSTRAINT ck_roles_valor CHECK (valor >= 0)
);
CREATE INDEX ix_roles_data ON roles (data, id);
