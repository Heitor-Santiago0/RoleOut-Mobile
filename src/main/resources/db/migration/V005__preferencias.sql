CREATE TABLE preferencias_conta (
    conta_id INT NOT NULL PRIMARY KEY,
    conteudo TEXT NOT NULL,
    CONSTRAINT fk_preferencias_conta FOREIGN KEY (conta_id) REFERENCES contas(id)
);
