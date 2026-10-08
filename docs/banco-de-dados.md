# Banco de desenvolvimento e validação

## Scripts versionados

`src/main/resources/db/migration/V001__roles.sql` cria uma tabela compatível com
os campos usados no DAO web, em um banco **novo**. Como o DDL anterior não está
no repositório, os tamanhos, índices e DECIMAL são escolhas para o novo esquema,
não uma reprodução confirmada do banco instalado anteriormente.

V002 prepara o cadastro de organizações e locais e acrescenta `roles.local_id`
sem substituir `roles.local`. Nenhum script contém DELETE, DROP ou TRUNCATE.
Não há dados fictícios embutidos nos scripts de produção; os testes criam seus
próprios exemplos em memória. Os registros web existentes foram informados
como dados de teste, mas nenhum banco existente foi acessado ou apagado aqui.

## Como aplicar

No Docker Compose, os dois scripts estão montados em modo leitura na pasta de
inicialização do MySQL. São executados em ordem apenas quando o volume do banco
é inicializado vazio. Um volume já existente não recebe esses scripts
automaticamente. O WAR também não executa migrações na inicialização.

Para banco existente, primeiro obter `SHOW CREATE TABLE roles;` e verificar se
`id` é INT com o mesmo sinal e se o mecanismo é InnoDB. Não executar V001 sobre
uma tabela existente. Aplicar V002 apenas após comparar os nomes/tipos e verificar
que ele ainda não foi aplicado. Esses arquivos numerados não têm um executor
de migrações nem histórico automático; serão adotados por um executor versionado
num próximo incremento. Não reaplicar V002: DDL no MySQL pode efetuar commits
implícitos e uma falha intermediária precisa ser inspecionada antes de retentar.

Como os dados são de teste, é possível iniciar uma base de desenvolvimento limpa
em um volume separado. Isso evita depender dos exemplos antigos, sem remover
o volume usado pela versão web. Não há instrução para apagar o volume atual.

## Validação realizada

Resultado local: `mvn -Dmaven.repo.local=.m2-repository -B -ntp verify` aprovado,
15 testes, zero falhas/erros, WAR gerado. Distribuição: 2 testes do pool,
4 testes de esquema/DAO e 9 testes da API. O timeout do pool nos testes é de
2 segundos, para tolerar inicialização/JIT do banco; na aplicação o padrão
continua sendo 5 segundos. Logs do Hikari são encaminhados ao Java Util Logging.

Os testes aplicam os dois scripts a bancos H2 isolados em modo MySQL e executam
os DAOs reais: inserir, listar, buscar, editar, excluir e filtrar sugestões.
Também verificam paginação, filtros preparados, relacionamentos e ocultação de
rolês vinculados a locais/organizações ainda não aprovados ou suspensos.

Isso testa o código e o esquema em H2. Não comprova equivalência com MySQL 8.4
nem renderização JSP. Docker não está instalado neste ambiente, e a validação
real MySQL/Tomcat permanece pendente.

## Checklist para o ambiente com Docker

1. Usar banco/volume de desenvolvimento separado e aplicar os scripts em ordem.
2. Executar `mvn clean verify` e depois `docker compose up -d` no ambiente preparado.
3. Abrir `/RoleOut/`, `/RoleOut/roles` e `/RoleOut/sugestoes`.
4. Cadastrar um rolê fictício, listar, editar data/preço, filtrar e excluir apenas
   esse exemplo. Confirmar caracteres acentuados e data ausente.
5. Consultar `/RoleOut/api/v1/roles` e `/RoleOut/api/v1/roles/{id}`; conferir
   resposta JSON, 404 para ID ausente, 400 para filtro inválido e 503 com banco parado.

Para uso público, substituir credenciais de desenvolvimento e resolver as
permissões e a exclusão via GET na versão web antes de expor a administração.

Referência MySQL: https://dev.mysql.com/doc/refman/8.4/en/create-table.html

V004 acrescenta roteiros privados e snapshots de paradas. Aplicação única e detalhes em roteiros-salvos.md; banco existente não recebe migrações automaticamente.
