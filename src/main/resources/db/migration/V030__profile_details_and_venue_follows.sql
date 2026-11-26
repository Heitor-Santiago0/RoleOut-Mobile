CREATE TABLE perfil_detalhes (
 conta_id INT PRIMARY KEY,
 foto MEDIUMBLOB NULL,
 interesses_publicos BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE
);
CREATE TABLE locais_seguidos (
 conta_id INT NOT NULL,
 local_id INT NOT NULL,
 PRIMARY KEY(conta_id, local_id),
 FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE CASCADE,
 FOREIGN KEY (local_id) REFERENCES locais(id) ON DELETE CASCADE
);
