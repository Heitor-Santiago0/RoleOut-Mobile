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
envio repetido retornam 409. Pendentes não podem ser editados. Editar um publicado
prepara uma nova proposta RASCUNHO, mantendo `roleId` e a publicação atual intactos.

A aprovação exige local PUBLICADO e organização APROVADA no instante da decisão.
Publicação, vínculo `roleId`, mudança de estado e histórico são gravados em uma
transação. A cidade e o endereço vêm do local aprovado; o preço vem do rascunho
revisado. O novo rolê fica disponível nas consultas públicas da API. Se o local ou
a organização forem suspensos, as consultas públicas deixam de exibi-lo.

O CRUD e as sugestões da versão web original continuam atendendo os registros sem
`local_id` (cadastros de teste). Não podem ler, editar ou excluir as novas publicações
vinculadas a locais, evitando contornar a revisão pela interface antiga. Os dados
antigos não foram removidos.

## Alterações e cancelamentos após publicação

Aplicar V014 (situação pública) e V015 (proposta e conteúdo da revisão), após as
migrações anteriores, antes do WAR atualizado. Colunas novas têm valores padrão;
nenhum evento ou roteiro é apagado. Não repetir scripts já aplicados.

Em **Meus locais → Rolês do local**, usar **Propor alteração ou cancelamento**.
Editar nome, descrição, categoria, data, início, término ou preço salva apenas a
proposta. Enviar para análise mantém a publicação anterior até a decisão. O revisor
vê a publicação atual e a proposta lado a lado na sequência da tela. Uma aprovação
atualiza o mesmo registro público, preservando seu identificador; rejeição mantém
a versão pública e permite corrigir a proposta. Autorização, versões e bloqueios
transacionais seguem obrigatórios. Responsáveis não aprovam a própria organização.

O PUT aceita também `cancelado` (boolean, padrão false) e `motivoCancelamento`
(obrigatório, até 2.000 caracteres, quando cancelado=true). Cancelamento exige um
evento já publicado. O motivo fica público após aprovação. Para reativar um evento
cancelado, propor `cancelado=false` e enviar novamente para análise; a reativação
também depende da aprovação. O cliente deve enviar o estado pretendido explicitamente.

Eventos cancelados são excluídos das listas públicas e do mapa, e não podem entrar
em novos roteiros. O GET público por ID continua disponível, respeitando a aprovação
do local/organização, com `cancelado` e `motivoCancelamento`, para explicar o ocorrido.
A abertura de direções consulta novamente esse detalhe e bloqueia eventos cancelados.

Cada nova decisão registra em `conteudo` o texto da publicação anterior e da proposta,
junto com versão, decisão, justificativa, revisor e data já existentes. O conteúdo é
imutável nas edições seguintes. Revisões antigas mantêm `conteudo=null`: não se tenta
reconstruir uma versão desconhecida. Publicação e auditoria são uma única transação.

Roteiros salvos conservam seus snapshots. Na consulta online, cada parada inclui
`cancelado` com a situação atual do catálogo e o aplicativo mostra um aviso. A cópia
offline guarda a situação da última atualização, sem prometer atualidade. Alterações
de data, horário e preço não recalculam automaticamente o roteiro histórico; o usuário
pode consultar o evento atual. Não há aviso push nesta entrega.

Validação: 95 testes Java e 55 testes mobile aprovados, TypeScript e lint sem erros.
Casos incluem proposta privada, aprovação no mesmo ID, rejeição, cancelamento,
reativação, autorização, versão desatualizada, rollback da publicação se falhar a
auditoria e preservação dos roteiros. Banco local preservou 22 rolês, 10 roteiros,
6 rascunhos e 10 revisões. Validação no celular ainda necessária: criar proposta,
revisar com administrador sem vínculo, abrir detalhe cancelado e consultar roteiro
online/offline. Nenhum cadastro real foi cancelado para executar os testes.



## Eventos que atravessam a meia-noite

`dataFim` é opcional (YYYY-MM-DD). Quando informada, exige data inicial e os dois horários; o instante final deve ser posterior ao início. O período é contínuo: não representa sessões recorrentes nem funcionamento diário do estabelecimento. O formulário do responsável, a fila de aprovação, o histórico e o detalhe público exibem essa informação. Alterações só entram em vigor após aprovação e mantêm o ID público.

Aplicar V016 (roles.data_fim) e V017 (rascunhos_roles.data_fim) sobre o esquema V015. São colunas anuláveis; os registros existentes conservam seu significado e os roteiros salvos não são reescritos.

Buscas por data aceitam dias entre início e término, inclusive. O planejamento usa a data escolhida: em dias posteriores ao início, não repete a abertura original. A visita não pode ultrapassar o término. No primeiro dia, permanece a regra atual de compromisso com o início publicado; horários de visita são relativos ao dia de partida. Roteiros seguem limitados a 12 horas. Recomendações por dia incluem eventos contínuos ainda vigentes, mas não afirmam disponibilidade em tempo real.

Validação automatizada: datas inválidas, virada de ano, limite exato de término, visita no último dia, rejeição sem gravação parcial, aprovação/alteração auditada, filtro por data e preservação dos roteiros existentes. No celular, validar formulário, aprovação, detalhe e salvar uma visita após a meia-noite. Sessões diárias, exceções de funcionamento e fusos IANA permanecem para próximas entregas.


## Funcionamento semanal e exceções por data

O responsável configura **funcionamento desta atividade no local** no formulário de rascunho. É uma programação por rolê, sem herança automática do local: oficinas e visitas no mesmo estabelecimento podem ter horários diferentes. A proposta, sua aprovação/rejeição e o histórico seguem as permissões e o controle de versão existentes. Uma edição mantém os horários publicados anteriores até ser aprovada.

- Dias semanais de segunda a domingo; até quatro intervalos por dia, com pausas.
- Até 60 exceções por data. Uma exceção substitui a semana inteira daquele dia.
- Dia sem regra: não informado, exige confirmação. Regra sem intervalos: fechado.
- Para funcionamento noturno, informar até 24:00 num dia e a continuação a partir de 00:00 no seguinte. Exceções de fechamento também se aplicam ao trecho após a meia-noite.
- Horários são locais da atividade. Não garantem vagas, reservas ou tempo de deslocamento.

A API usa `funcionamento: [{dia: "5", intervalos: [{inicio: "09:00", fim: "12:00"}, {inicio: "14:00", fim: "18:00"}]}]`. Dia 1 é segunda; 7, domingo. Uma data ISO no campo `dia` indica exceção. `null` ou lista vazia mantém funcionamento desconhecido. O cadastro rejeita chaves duplicadas, datas inválidas, sobreposições e intervalos invertidos. O limite de corpo do endpoint de rascunhos é 64 KiB; os demais mantêm 16 KiB.

Ao organizar/salvar, o app verifica a visita completa e o servidor consulta novamente os dados aprovados. Uma visita que cruza pausa ou fechamento é recusada com `FUNCIONAMENTO_INCOMPATIVEL`; a gravação é revertida sem roteiro parcial. A ordenação sugerida mantém a ordem anterior quando a proposta conflita. Não há deslocamento ou espera automática para uma próxima abertura. Regras de datas e horários do evento continuam valendo junto com o funcionamento.

V018 e V019 adicionam colunas TEXT anuláveis contendo JSON validado, respectivamente em `roles` e `rascunhos_roles`. Não reescrevem registros nem roteiros salvos. Roteiros históricos/offline mantêm o planejamento original. A conferência online de alterações está documentada em `roteiros-salvos.md`.

Testes cobrem persistência/aprovação/rejeição, histórico, API autenticada e limite de corpo, exceções, pausas, limites exatos, madrugada, dados inválidos e preservação de roteiros. No celular: abrir o editor pelo perfil responsável, informar semana/exceção, enviar/aprovar com administrador independente, conferir detalhes e tentar salvar uma visita durante fechamento. Conferir também a rolagem com teclado aberto.
