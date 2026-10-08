# Preferências opcionais

Após login, `/preferences?onboarding=1` consulta as preferências. Contas sem
resposta recebem quatro etapas: música, comida, atividades e ambientes.
É possível selecionar várias opções, deixar categorias vazias ou pular.
Respostas concluídas/puladas não são perguntadas novamente no próximo login.
`Conta > Minhas preferências` permite editar ou remover seleções.

As opções são um catálogo inicial proposto; os quatro grupos seguem o escopo
do Figma, mas os textos/opções ainda precisam de revisão visual com o usuário.
As seleções são armazenadas para futura personalização. Ainda não mudam filtros,
ordenação do Explorar ou geração de roteiros. Não há promessa de recomendação
automática nesta etapa.

`GET /api/v1/preferencias` retorna `{perfil, opcoes}`. Perfil contém status
PENDENTE, CONCLUIDO ou PULADO e arrays musica, comida, atividades e ambientes.
`POST` recebe os quatro arrays e `pular` booleano; substitui as escolhas da
conta autenticada. Pular grava arrays vazios. O servidor valida o catálogo,
duplicatas e campos; a conta vem apenas do token. Gravações são serializadas
por conta e usam uma única conexão do pool.

V005 cria `preferencias_conta` e não altera contas/rolês existentes. Foi aplicada
uma vez no volume de integração após confirmar ausência da tabela. A instalação
web antiga não recebeu essa migração. Novos volumes aplicam V001–V005; volumes
existentes precisam de conferência antes da aplicação manual.

Validação em 08/10/2026: 50 testes Java, tipos, lint e bundles web/Android/iOS
aprovados. Teste HTTP com MySQL/Tomcat verificou gravar/ler, gravação repetida,
rejeição de opção inválida, pular e isolamento entre contas, além da regressão
de login e roteiros. O usuário confirmou o cadastro pela interface antes desta
etapa. A automação do navegador continua indisponível; inspeção visual destas
novas telas e teste em aparelho ainda pendentes.
