# Roteiros privados salvos na conta

## Contrato

Requer V001, V002, V003 e V004. Todas as consultas e gravações exigem
Authorization: Bearer token. O dono vem da sessão, nunca do JSON.

- POST /api/v1/roteiros: cria e devolve {roteiro, paradas}, HTTP 201.
- GET /api/v1/roteiros?deslocamento=0: devolve {itens, limite:20}, mais recentes primeiro.
- GET /api/v1/roteiros/{id}: detalhe privado. Outro usuário recebe 404.

Exemplo de corpo (o ID da parada deve existir no banco):

```json
{
  "nome": "Passeio de sábado",
  "cidade": "Uberaba",
  "data": "2026-10-15",
  "partida": "10:00",
  "duracaoMinutos": 240,
  "pessoas": 3,
  "orcamentoCentavos": 5000,
  "modoOrcamento": "person",
  "paradas": [{"roleId": 1, "minutos": 60}]
}
```

O valor informado é por pessoa em person ou total em group. O servidor converte
para limite total e o devolve em roteiro.orcamentoCentavos. Para person=5000 e
pessoas=3, o limite armazenado é 15000. Os preços vêm do banco e são recalculados;
o cliente não pode informar total, preço ou contaId. Campos desconhecidos e
números fracionários em campos inteiros são rejeitados.

Validações: data válida de 2000 a 2100, partida HH:MM, duração 60 a 720 min,
1 a 50 participantes, 1 a 48 paradas distintas, cada visita de 15 a 720 min em
múltiplos de 15. Soma das visitas deve caber na duração; soma dos preços × pessoas
deve caber no limite. No máximo 100 roteiros por conta, com trava da linha da conta
durante a gravação para manter esse limite em salvamentos concorrentes.

Os rolês precisam estar publicados conforme o filtro da API, pertencer à cidade
e, se têm data específica, corresponder ao dia planejado. Registros legados sem
local_id ainda seguem a compatibilidade de desenvolvimento da API existente.
Uma parada alterada, removida ou sem publicação retorna 409; preço que ultrapassa
o orçamento também retorna 409. Nenhum cabeçalho/parada parcial fica gravado.

## Histórico e interface

Cabeçalho e paradas são gravados em transação. Nome, local e preço de cada parada
são cópias históricas: exclusão de um rolê pela web não quebra roteiros anteriores.
Não há FK entre o ID original do rolê e as paradas; a consulta ao detalhe atual
pode retornar 404 após exclusão. As cópias são visíveis apenas ao dono do roteiro.

O app envia somente IDs/tempos das paradas e parâmetros do planejamento.
Ao salvar, informa o total confirmado pelo servidor e oferece abrir o registro.
Conta -> Meus roteiros lista os registros, e cada detalhe mostra os preços e tempos
registrados, com aviso para confirmar disponibilidade. Roteiros são somente leitura
neste incremento; alterar a montagem e salvar cria um novo registro. Demonstração
não grava; não há armazenamento local de tokens fora do mecanismo já existente.

Não há idempotência automática de POST neste incremento. Se a resposta se perde
após commit, o app orienta consultar Meus roteiros antes de repetir, pois um novo
envio pode duplicar o roteiro. Isso deve ser resolvido antes de operação em escala.
Não há edição, remoção, compartilhamento, reservas ou otimização de trajetos.

## Aplicação da migração e limites de validação

V004 apenas cria duas tabelas e um índice; não altera/apaga roles ou JSPs.
O Compose executa V004 somente em volume novo, em ordem após V003. Banco existente
exige conferência do esquema e execução única do script; o WAR não executa migrações.
Nenhum banco MySQL existente foi acessado ou alterado nesta tarefa.

Testes H2 validam salvamento/recuperação, isolamento, preços do servidor,
rollback, publicação, cópias após exclusão web e operação com pool de tamanho 1.
Testes de servlet validam autenticação, escopo e JSON. Isso não substitui o teste
com MySQL/Tomcat real nem o fluxo completo do app autenticado no aparelho.
O backend não oferece CORS para a prévia web; não foi aberto acesso por CORS neste
incremento. A demonstração do navegador não comprova persistência real.


## Alertas de horários dos roteiros salvos

Em Meus rolês e no detalhe de um roteiro, o painel **Atualizações dos horários** consulta os dados publicados ao abrir/retomar a tela. **Verificar alterações** refaz a consulta. Mostra alterações de datas, início, término e funcionamento; conflitos com a visita planejada; cancelamentos e cadastros indisponíveis. Paradas concluídas não geram novos alertas, mas continuam contando na linha do tempo. A consulta é privada e não altera planejamento, preços ou progresso.

Novos roteiros capturam os horários publicados em `roteiro_paradas.horarios_originais` (V020, TEXT anulável). Essa referência permanece imutável. Roteiros anteriores conservam NULL: recebem aviso de ausência de histórico e a compatibilidade é conferida com o cadastro atual, sem afirmar que ocorreu uma mudança. Não há atualização automática do histórico ao consultar avisos.

Endpoints autenticados somente GET: `/roteiros/{id}/verificar` retorna `verificadoEm`, `paradasVerificadas`, `alertas[{roleId,nome,codigo,mensagem}]`; `/roteiros/alertas` retorna `itens[{id,nome,verificacao}]` dos roteiros com paradas pendentes, até o limite de 100 da conta. A lista é restrita ao dono; consultar o ID alheio retorna 404. Códigos: HORARIOS_ALTERADOS, HORARIO_INCOMPATIVEL, SEM_HISTORICO, CANCELADO, INDISPONIVEL.

Uma falha de rede remove a confirmação anterior do painel e mostra que a conferência não foi possível. As cópias offline continuam disponíveis, mas não recebem validação atual nem esses alertas. Os alertas internos estão disponíveis. O fluxo Android de push com o app fechado está implementado, mas sua ativação depende de Firebase/FCM e de novo APK; veja [notificações push](notificacoes-push.md). Valores, vagas e deslocamentos não são revalidados por este endpoint.

Testado: comparação de horários e funcionamento, mudança compatível/incompatível, histórico ausente, cancelamento/remoção, repetição da consulta sem apagar alertas, isolamento entre contas, exclusão de concluídos e preservação do roteiro. Validar no celular: salvar novo roteiro, aprovar mudança do evento em outra conta, voltar a Meus rolês, consultar os detalhes e repetir sem rede. O horário original do roteiro deve permanecer igual.
