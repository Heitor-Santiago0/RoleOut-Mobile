# Publicação independente do notebook

Estado em 26/11/2026: **API e MySQL publicados no Railway**, em projeto separado do Entec/Guardian. Nenhum plano pago foi contratado; ambos consomem os créditos disponíveis da mesma conta.

- API: https://roleout-api-production.up.railway.app/RoleOut/api/v1
- Projeto: https://railway.com/project/3ffc6bfd-c4cf-4244-98bc-6be56f269d0d
- [Baixar APK independente com capas no feed](https://expo.dev/artifacts/eas/MOfR7bFLxbY68L8nbCy1Tcv9jmubar3w0tYaPslWR9A.apk), 136,8 MB. [Build concluído, FINISHED](https://expo.dev/accounts/toledosantiago0/projects/roleout-mobile/builds/a99cc0a6-a503-4cde-8772-77968c89d0c9).
- Arquivo local: `mobile/.expo/apks/roleout-feed-2026-11-26.apk`, ignorado pelo Git. SHA-256: `c08599c8d52ab991b6ea6f7d950bda7a7f45ea13e9509eb4b12d2968b49636d8`. Integridade ZIP, pacote `br.com.roleout.mobile`, código das capas e URL HTTPS verificados; URL local ausente do bundle. Instalação e validação física desta versão ainda pendentes; o usuário confirmou o funcionamento do APK anterior de perfil/catálogo.
- API do feed publicada: deploy `0f6faa29-c977-42cf-8d1d-691fc7a8eacb`, 188 testes Java e healthcheck aprovados. Sem migração nesta atualização. [Entrega e testes do feed](feed-visual.md).
- Migração preservou 32 contas, 12 roteiros e 22 rolês. Backup privado em `tmp/roleout-before-cloud.sql`, ignorado pelo Git. Pacote de transferência criptografado; serviços temporários de importação e limpeza removidos após verificação.
- Banco sem proxy TCP público; API usa `roleout_api` com SELECT/INSERT/UPDATE/DELETE, sem privilégios administrativos. Volume persistente separado do Guardian.
- MySQL configurado com buffer de 128 MB e máximo de 30 conexões. API com pool máximo de quatro conexões. `PUSH_ENABLED=true`; avisos dependem da disponibilidade e do saldo da hospedagem.
- Testes HTTPS aprovados: catálogo, cadastro/login, perfil da conta, roteiro persistido, isolamento entre duas contas, preferências, perfil social, consulta da configuração de notificações, exclusão do roteiro de teste e revogação das sessões. As duas contas temporárias também foram removidas; contagens originais confirmadas novamente.

O projeto Entec não foi alterado. Novos dados gravados no RolêOut online não são sincronizados automaticamente com o banco local.

Validação local desta preparação: imagem Docker construída com `mvn verify` aprovado; Tomcat iniciou como usuário sem privilégios; catálogo retornou HTTP 200 consultando o MySQL existente; `/roles` e `/index.jsp` retornaram 404; roteiros sem autenticação retornaram 401. O banco não foi migrado nem alterado por estes testes HTTP. A execução Maven direta no Windows foi interrompida após ficar sem progresso; a verificação completa foi concluída dentro da imagem Linux.

## Imagem da API

O `Dockerfile` compila e testa o WAR com Java 17 e executa Tomcat 10.1 como usuário sem privilégios. A lista de inclusão do `.dockerignore` envia somente código Java, testes, migrações, `pom.xml` e o iniciador; não inclui `.env`, credenciais Firebase, banco local, APKs ou chaves de assinatura.

```
docker build -t roleout-api .
```

Variáveis privadas obrigatórias: `DB_URL`, `DB_USER`, `DB_PASSWORD`. O iniciador recusa configuração ausente. `PORT` é a porta HTTP interna, padrão 8080, entre 1024 e 65535. O provedor ou proxy deve fornecer HTTPS público. Para MySQL externo à rede privada, exigir TLS com validação do certificado conforme as instruções do provedor; não desabilitar validação para contornar erro de conexão.

O pool usa até quatro conexões, com mínimo ocioso zero. A imagem define `ROLEOUT_PUBLIC_DEPLOYMENT=true`: somente `/api/v1/*` fica acessível. O CRUD JSP antigo (`/roles`, incluindo exclusão por GET) é bloqueado com 404 na imagem pública. O ambiente local de aula mantém suas rotas. Não desativar essa proteção no servidor público.

Validar a conexão real pelo `GET /RoleOut/api/v1/roles?limite=1`, que consulta o banco, além de login, isolamento entre contas e consulta de roteiros. Uma página inicial do Tomcat não comprova funcionamento da API.

## Banco e preservação dos dados

Manter MySQL 8.4 e as migrações V001–V029. Antes da transferência, fazer backup privado e conferir contagens das tabelas. Importar o dump somente em banco remoto vazio; não executar todas as migrações novamente sobre o dump. A API deve usar usuário específico com permissões de leitura/escrita necessárias, sem acesso administrativo. O MySQL não deve ficar aberto publicamente.

Fotos de publicações ficam no banco: considerar isso no limite de armazenamento. Não apagar o volume para reconstruir a API. Testar restauração e definir rotina de backup fora do repositório. Após a transferência, dados novos no notebook não sincronizam automaticamente com a nuvem.

## APK independente

O perfil EAS `preview` usa `developmentClient: false`. O ambiente preview foi configurado com `EXPO_PUBLIC_API_URL=https://roleout-api-production.up.railway.app/RoleOut/api/v1`, chave Google Maps e arquivo Firebase privado no EAS. A assinatura existente foi preservada. O ambiente development/local continua separado. O APK antigo de desenvolvimento ainda depende do Metro: instalar o novo APK preview para abrir sem o notebook.

## Atualizar a API

A publicação usa CLI com uma pasta de envio contendo somente `Dockerfile`, `.dockerignore`, `pom.xml`, `src` e `deploy/start.sh`. Não há vínculo automático com GitHub. Não enviar a raiz do projeto com `--no-gitignore`.

No serviço `roleout-api`, configurar Dockerfile `Dockerfile`, porta interna 8080 e healthcheck `/RoleOut/api/v1/roles` com timeout de 180 segundos. As configurações foram salvas diretamente no serviço; não dependem do formato `railway.json`, descontinuado pelo provedor. Atualizações exigem teste, nova publicação explícita e verificação HTTPS. Não reimportar o backup sobre o banco online populado.

Validar no celular com notebook desligado: abertura, login, catálogo/mapa, roteiros, sincronização, imagens e notificações. O acesso a roteiros offline continua limitado aos dados já salvos no aparelho.

Para apresentar: baixar o APK no celular, instalar sobre a versão anterior sem desinstalar, abrir e entrar com a conta habitual. Não é necessário digitar endereço de servidor. Desconectar o cabo e desligar o servidor local para conferir login, mapa e um roteiro usando internet. Baixar um roteiro e conferir também a consulta offline. A validação no aparelho não é substituída pelos testes automatizados.

## Gratuidade e notificações

O Entec/Guardian foi hospedado no Railway usando créditos temporários de teste; isso não comprova hospedagem gratuita permanente. Segundo as páginas oficiais consultadas em 26/11/2026, Railway Free oferece US$ 1 de consumo mensal, um projeto, três serviços e suspensão ao esgotar saldo. Serviços podem dormir quando ociosos. O teste oferece US$ 5 por até 30 dias. Não criar recursos no saldo do Guardian sem avaliar o impacto sobre aquele projeto.

O agendador de avisos atual consulta o banco a cada minuto com `PUSH_ENABLED=true`. Ele precisa de servidor executando: em hospedagem que dorme, avisos podem atrasar; em planos por consumo, ele pode manter serviços acordados e gastar o saldo. Não prometer notificações contínuas nesse cenário nem criar requisições artificiais para contornar suspensão.

Oracle Always Free é uma alternativa a avaliar para VM e MySQL próprios, sem contratar plano pago; cadastro exige cartão de verificação e a criação depende de capacidade disponível. Conferir recursos elegíveis e limites diretamente na conta antes de provisionar. Nenhuma opção gratuita é promessa de disponibilidade permanente.

Referências: [Railway Free](https://docs.railway.com/pricing/free-plan), [Railway preços](https://railway.com/pricing), [Oracle Free Tier FAQ](https://www.oracle.com/cloud/free/faq/).

## Atualização social de 26/11/2026

Novo APK inclui Feed, perfil simplificado com foto e interesses opcionais, seguir locais, categorias ampliadas e fotografias reais de Uberaba. Veja [implementação e testes](perfil-feed-locais.md). APK validado por integridade ZIP, pacote Android, novos endpoints, três fotografias e URL HTTPS embarcada. Teste físico desta atualização ainda pendente. A versão online anterior foi testada pelo usuário.
