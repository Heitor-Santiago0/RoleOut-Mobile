# Login, permissões e cadastro em rascunho

Este incremento acrescenta endpoints ao WAR, sem modificar as páginas JSP
existentes. Requer V001 + V002 + **V003__sessoes.sql**. O Docker aplica os três
scripts apenas num volume vazio; em banco existente, aplicar apenas a versão
pendente após conferir o esquema. Ainda não há executor automático de migrações.

## Conta e login

Prefixo: `/RoleOut/api/v1`.

| Método | Caminho | Entrada/resultado |
| --- | --- | --- |
| POST | /auth/cadastro | nome, email, senha -> conta, HTTP 201 |
| POST | /auth/login | email, senha -> token, expiraEm, conta |
| GET | /auth/me | Conta autenticada |
| POST | /auth/logout | Revoga o token atual, HTTP 204 |

Enviar corpos JSON com `Content-Type: application/json`. Campos desconhecidos,
JSON nulo, documentos concatenados e corpos grandes são rejeitados. O limite
é 16 KiB para Content-Length, mais 16.384 caracteres na leitura de corpo sem
tamanho declarado. A senha tem entre 6 e 128 caracteres e não é normalizada
nem removida de espaços. E-mail é normalizado para minúsculas e deve ser único.

Senha armazenada com PBKDF2-HMAC-SHA256, 600.000 iterações, salt aleatório de
16 bytes e hash de 32 bytes. O formato contém algoritmo/iterações para futura
migração. Comparação do resultado em tempo constante. O login usa verificação
dummy para e-mail ausente e mantém resposta genérica para credenciais incorretas.

Token opaco aleatório de 32 bytes, validade absoluta de 24 horas. Somente seu
digest SHA-256 é persistido. Enviar `Authorization: Bearer <token>` nas operações
protegidas. Token não vai em URL nem em cookie; não há autenticação por cookies
nesses endpoints. Logout apaga a sessão atual. Limite de 10 sessões ativas por
conta; novas entradas removem sessões expiradas dessa conta.

Cadastro/login: até 10 tentativas por minuto por endereço visto pelo servidor,
com armazenamento limitado a 10.000 endereços por instância. O limitador não
confia em X-Forwarded-For. Hashing limitado a duas operações simultâneas; excesso
recebe HTTP 429. Com proxy, NAT ou réplicas, configurar limite apropriado no
gateway compartilhado. Revisar esse limite com testes de carga do ambiente real.

Cadastro público nunca concede administrador. V003 adiciona o campo com padrão
FALSE; não há endpoint para promover contas. A confirmação de e-mail ainda não
foi implementada: contas continuam com emailConfirmado=false e podem salvar
rascunhos. Não existe envio para análise nem publicação neste incremento.

## Organizações e locais

Todas as operações seguintes exigem token válido.

| Método | Caminho | Uso |
| --- | --- | --- |
| GET/POST | /organizacoes | Listar minhas organizações/criar organização |
| GET/PUT | /organizacoes/{id} | Consultar/atualizar organização própria |
| GET/POST | /organizacoes/{id}/locais | Listar/criar locais próprios |
| GET/PUT | /organizacoes/{id}/locais/{localId} | Consultar/atualizar local próprio |

Listas limitadas aos primeiros 100 registros por ID, sem total. Paginação dessas
listas será acrescentada ao painel. PUT substitui os campos editáveis do cadastro;
campos opcionais omitidos ficam nulos. Sem endpoints DELETE neste incremento.

Organização recebe `nome`, `tipo`, `descricao`, `contatoEmail`, `contatoTelefone`.
Tipos: RESTAURANTE, BAR, MUSEU, PREFEITURA, OUTRO. Criação e associação da conta
como RESPONSAVEL acontecem numa transação, sem aceitar conta/papel do cliente.
O cadastro inicia PENDENTE. Só o RESPONSAVEL pode editar a organização, e apenas
nos estados PENDENTE/REJEITADA. Correção de rejeição volta a PENDENTE.

Local recebe `nome`, `categoria`, `endereco`, `cidade`, `uf`, `cep`, `descricao`,
`acessibilidade`. Os cinco primeiros campos são obrigatórios; UF deve ser válida.
Local inicia RASCUNHO. RESPONSAVEL e EDITOR podem criar/editar locais da própria
organização, mas organizações SUSPENSAS bloqueiam gravação. Só locais RASCUNHO
ou REJEITADO podem ser editados; a correção de rejeição volta a RASCUNHO.
Coordenadas, horários, fotos e gestão de membros ficam para incrementos seguintes.

O vínculo é sempre verificado no servidor. Um local não pode ser movido para
outra organização pelo corpo ou pelo caminho. Organização ou local de terceiros
responde 404 para não revelar sua existência. Nenhum campo `status`, `papel`,
`administrador`, `contaId` ou `organizacaoId` é aceito no corpo de cadastro.

## Exemplo de cadastro

POST `/organizacoes`, com o token obtido no login:

```json
{"nome":"Museu Exemplo","tipo":"MUSEU","contatoEmail":"contato@example.com"}
```

Usar o ID devolvido em POST `/organizacoes/{id}/locais`:

```json
{"nome":"Sede do Museu","categoria":"Cultura","endereco":"Rua Exemplo, 10","cidade":"Uberaba","uf":"MG","acessibilidade":"Entrada com rampa"}
```

## Limites da entrega

Contrato OpenAPI do incremento: `openapi-cadastros.json`, que complementa
`openapi.yaml` (consultas públicas). Testes: 28 no total, incluindo cadastro,
login/logout, sessão expirada, hashes aleatórios, separação entre organizações,
rollback de cadastro, permissões de editor, bloqueio de edição de publicados,
campos extras indevidos, limite de corpo e tentativas de login.

Implementados API, validações, senhas/sessões, controle por organização e testes
isolados. Ainda faltam telas, confirmação de e-mail, recuperação de senha,
convite de membros, revisão administrativa e publicação. O painel será o próximo
consumidor desses endpoints. A aprovação exigirá conta administradora, e-mail
confirmado, transições controladas e auditoria transacional antes da publicação.

Para implantação: HTTPS e armazenamento seguro do token no cliente. A
administração web legada ainda não usa este login nem suas permissões; mantê-la
em ambiente de desenvolvimento até receber proteção própria. Testes H2 e
servlets simulados não substituem a validação MySQL/Tomcat, ainda pendente.

Referências: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
e https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKeyFactory.html
