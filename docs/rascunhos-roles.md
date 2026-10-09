# Rascunhos de rolês dos locais

Fluxo editorial: criação, edição, envio, revisão auditada e publicação.
O formulário mobile está em Minha conta → Meus locais → organização → Rolês do local.
O administrador encontra a fila em Analisar cadastros → Rolês para análise.

Aplicar V006 e V007 após as migrações anteriores. As novas tabelas não alteram os rolês de teste
do site. Nenhuma consulta pública ou consulta do site lê `rascunhos_roles`.

Todas as operações exigem Bearer token e vínculo da conta com a organização do local.
Escritas exigem organização APROVADA e local PUBLICADO; leitores autorizados ainda
podem consultar rascunhos após suspensão. Não existe atribuição livre de cidade,
endereço, organização ou estado de publicação pelo cliente.

| Método | Endpoint | Resultado |
|---|---|---|
| GET | `/api/v1/rascunhos-roles/{localId}?deslocamento=0` | `{ "itens": [...] }`, até 20 por página |
| POST | `/api/v1/rascunhos-roles/{localId}` | Rascunho criado, HTTP 201 |
| PUT | `/api/v1/rascunhos-roles/{localId}/{rascunhoId}` | Rascunho atualizado, HTTP 200 |
| POST | `/api/v1/rascunhos-roles/{localId}/{rascunhoId}/enviar` | Corpo `{"versao":1}`, transição para PENDENTE |
| GET | `/api/v1/rascunhos-roles/{localId}/{rascunhoId}/revisoes` | Até 100 decisões mais recentes, somente membros |
| GET | `/api/v1/revisoes-roles?deslocamento=0` | Até 20 rolês pendentes, somente administradores |
| POST | `/api/v1/revisoes-roles` | Decisão administrativa, HTTP 200 |

Corpo de criação:

```json
{"nome":"Visita à exposição","descricao":"Visita ao acervo","categoria":"Cultura","data":"2027-02-28","valor":12.35}
```

`valor` é o custo estimado **por pessoa**, em reais; zero significa gratuito.
Aceita até duas casas decimais, de zero a 99999999.99. `data` aceita ISO AAAA-MM-DD
ou null para uma atividade sem data específica; não implica reserva ou disponibilidade.
`descricao` é opcional. Edição exige `versao` da última resposta. A versão aumenta a
cada edição e uma tentativa desatualizada recebe 409, sem sobrescrever outro editor.

Dados fora da organização recebem 404; estado incompatível recebe 409; JSON/campos
inválidos recebem 400. O cliente não deve repetir automaticamente um POST após falha
de rede: deve consultar os rascunhos antes de tentar novamente.

Decisão: `{"rascunhoId":1,"versao":2,"decisao":"APROVAR","justificativa":"Dados conferidos"}`.
Também aceita REJEITAR. A justificativa é obrigatória, até 2000 caracteres. O
administrador não pode ter vínculo com a organização. O servidor consulta a permissão
atual a cada operação; nenhum campo do cadastro público permite criar administradores.

Estados: RASCUNHO → PENDENTE → PUBLICADO ou REJEITADO. Rejeitados podem ser corrigidos
e reenviados. Edição, envio e decisão incrementam a versão; revisão desatualizada e
envio repetido retornam 409. Pendentes e publicados não podem ser editados nesta etapa.

A aprovação exige local PUBLICADO e organização APROVADA no instante da decisão.
Publicação, vínculo `roleId`, mudança de estado e histórico são gravados em uma
transação. A cidade e o endereço vêm do local aprovado; o preço vem do rascunho
revisado. O novo rolê fica disponível nas consultas públicas da API. Se o local ou
a organização forem suspensos, as consultas públicas deixam de exibi-lo.

O CRUD e as sugestões da versão web original continuam atendendo os registros sem
`local_id` (cadastros de teste). Não podem ler, editar ou excluir as novas publicações
vinculadas a locais, evitando contornar a revisão pela interface antiga. Os dados
antigos não foram removidos. Não há edição pós-publicação ou suspensão de rolê
individual neste incremento; esses fluxos exigem revisão própria.

