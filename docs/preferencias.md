# Preferências opcionais

Após login, `/preferences?onboarding=1` consulta as preferências. Contas sem
resposta recebem quatro etapas: música, comida, atividades e ambientes.
É possível selecionar várias opções, deixar categorias vazias ou pular.
Respostas concluídas/puladas não são perguntadas novamente no próximo login.
`Conta > Minhas preferências` permite editar ou remover seleções.

As opções são um catálogo inicial proposto; os quatro grupos seguem o escopo
do Figma, mas os textos/opções ainda precisam de revisão visual com o usuário.
O Explorar usa as escolhas para destacar até três rolês entre os resultados já
carregados da busca, sem substituir os filtros ou reordenar a lista completa.
Cada destaque explica a afinidade de categoria. Mais resultados podem produzir
novos destaques ao paginar; não se trata de uma classificação de todo o catálogo.
Eventos com data anterior ao dia local não são destacados. Data ausente não
comprova disponibilidade. Os roteiros continuam sendo montados manualmente.

Música escolhida se relaciona à categoria Música, comida a Gastronomia; atividades
usam relações explícitas como Museus/Cinema → Cultura e passeios ao ar livre →
Natureza. O texto não afirma que todo rolê cultural seja um museu. Gênero musical,
cozinha e ambientes como tranquilo/romântico não são inferidos de nome/descrição.
O catálogo atual não possui campos suficientes para essa precisão. Preferências
vazias/puladas mantêm a busca normal e oferecem o acesso à edição.

As preferências são recarregadas quando Explorar recebe foco. Uma falha nessa
consulta não bloqueia a busca; erro de autenticação encerra a sessão. O componente
é recriado quando o token muda para evitar mostrar interesses de outra conta.

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

Incremento dos destaques: 12 testes mobile (8 de roteiro e 4 de afinidade), tipos,
lint e bundles web/Android/iOS. Os testes cobrem limites, deduplicação, datas,
preferências puladas, categorias desconhecidas e explicações sem afirmar dados
específicos não cadastrados. Inspeção visual ainda pendente.
