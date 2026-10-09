# Rascunhos de rolês dos locais

Primeira etapa do fluxo editorial: API privada de criação, consulta e edição.
O formulário mobile está em Minha conta → Meus locais → organização → Rolês do local. Ainda não há envio para revisão ou publicação destes rascunhos.

Aplicar V006 após as migrações anteriores. A nova tabela não altera os rolês de teste
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

Próximas etapas: envio para revisão, decisão administrativa
auditada e criação do rolê público apenas após aprovação. A aprovação do local
não publica automaticamente as novas ofertas.

