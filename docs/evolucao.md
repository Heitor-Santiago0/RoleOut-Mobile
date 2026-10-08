# Etapa 1: pool e plano de evolução

Branch: `develop/api-pool`. A aplicação continua sendo um WAR Java 17, Jakarta
Servlet 6, JSP/JSTL e MySQL 8.4 para Tomcat 10.1. Nenhuma página ou rota web foi
substituída. Controllers -> RoleService -> RoleDAO continuam em uso.

Prioridades do próximo incremento: `mvp-academico.md`. A base de API não exige
finalizar todas as funções de administração antes de iniciar o primeiro fluxo
visual do app. O cadastro público de locais continua sujeito à aprovação.

## Banco e concorrência

ConnectionFactory agora delega ao DatabaseManager Singleton. Há um gerenciador
por classloader da aplicação, não uma Connection global. Inicialização do pool
é sincronizada; a espera por conexão acontece fora do monitor. Cada operação
do DAO recebe uma conexão exclusiva enquanto a utiliza e o try-with-resources
devolve a conexão ao pool. O listener fecha o pool ao remover/parar o WAR.

Configuração por ambiente (valores iniciais para desenvolvimento):

| Variável | Padrão |
| --- | --- |
| DB_URL | jdbc:mysql://localhost:3306/roleout |
| DB_USER | roleout |
| DB_PASSWORD | roleout123 |
| DB_POOL_MAX_SIZE | 10 |
| DB_POOL_MIN_IDLE | 2 |
| DB_POOL_CONNECTION_TIMEOUT_MS | 5000 |

Mantidas as credenciais locais anteriores para compatibilidade. Para implantação,
fornecer credenciais próprias por ambiente. Timeout de conexão MySQL: 5 segundos;
leitura de socket: 10 segundos. Tomcat pode subir antes do MySQL; as aquisições
continuam limitadas pelo timeout. Sob saturação ocorre SQLException, hoje
encapsulada pelos DAOs. O pool limita conexões, não garante capacidade ilimitada:
dimensionar com medições e considerar o total de instâncias da aplicação.

Testes usam H2 em memória, dois slots, oito workers e 32 consultas, além de
timeout quando ambos os slots estão ocupados, devolução, Singleton, configuração
inválida e fechamento. Não representam teste de carga nem integração MySQL/JSP.

## Próximas etapas, uma por vez

1. Scripts V001/V002 e testes de compatibilidade DAO foram adicionados. V001 é
   um baseline novo inferido do código; não é uma cópia confirmada do banco antigo.
   Os rolês antigos são exemplos de teste. Validar em MySQL/Tomcat e comparar o
   DDL antes de aplicar V002 a um banco existente: ver `banco-de-dados.md`.
2. API GET em `/api/v1` adicionada no mesmo WAR, com DTO JSON, datas ISO 8601,
   paginação e erros sanitizados. Contratos e limites em `api-rest.md` e
   `openapi.yaml`. O armazenamento novo usa DECIMAL e o DTO BigDecimal, mas
   migrar o modelo/DAO legado de double para BigDecimal ainda está pendente.
3. Cadastro de organizações e locais entrou no escopo, com aprovação antes da
   publicação confirmada pelo usuário. Estrutura SQL preparada; fluxos,
   permissões e telas adicionais definidos em `cadastro-de-locais.md`.
   Login e escrita de rascunhos agora implementados: ver `login-e-rascunhos.md`.
   Senhas com hash, sessões revogáveis, limites de tentativas e autorização por
   organização estão testados. Confirmação de e-mail e aprovação seguem pendentes.
   Usar HTTPS na implantação. Revisar também exclusão web via
   GET, validações numéricas e tratamento de erros encontrados na inspeção.
4. App separado React Native + Expo + TypeScript consumindo a API, sem JDBC ou
   credenciais MySQL no aparelho. Reobter as imagens Figma: a referência da
   conversa informa 12 imagens, mas não fornece seu conteúdo aqui.
5. Implementar login, onboarding opcional (música, comida, atividades, ambientes),
   Explorar, detalhes, perfil e roteiros personalizados em incrementos testáveis.

Não executar TesteConexao como teste automático: ele altera e exclui o primeiro
registro encontrado. Validar integrações em banco de teste descartável.

## Validação local

`mvn clean verify` compila, executa JUnit e gera `target/RoleOut.war`.
O H2 é dependência somente de teste; o WAR continua contendo o driver MySQL.
As variáveis H2 do Surefire se aplicam apenas ao processo de testes.

Referência: https://github.com/brettwooldridge/HikariCP/tree/HikariCP-5.1.0
