# Integração local com MySQL e Tomcat

Validada em 08/10/2026. O ambiente usa `compose.integration.yml`, projeto
`roleout-integration`, volume próprio e porta **8082**, acessível somente neste
computador. O MySQL não publica porta no host. As credenciais desse arquivo são
exclusivas de desenvolvimento local.

Os containers antigos `roleout-mysql` e `roleout-tomcat`, na porta 8080, continuam
usando a cópia da Área de Trabalho. Não foram recriados nem receberam migrações.

Na raiz do repositório, em PowerShell:

```powershell
mvn '-Dmaven.repo.local=.m2-repository' -B -ntp verify
docker compose -f compose.integration.yml up -d
# Aguardar o Tomcat terminar de inicializar antes do teste.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-integration.ps1
```

As migrações V001 a V004 são aplicadas pelo MySQL apenas ao inicializar um volume
vazio. Um volume existente não é atualizado automaticamente. Depois de recompilar
o WAR, reiniciar somente o Tomcat de integração:

```powershell
docker compose -f compose.integration.yml restart tomcat
```

Web: http://127.0.0.1:8082/RoleOut/

API: http://127.0.0.1:8082/RoleOut/api/v1

## Resultado

40 testes automatizados e build WAR aprovados. O teste HTTP com MySQL 8.4 e
Tomcat 10.1 passou para cadastro, login, identidade, detalhe de rolê, salvar,
listar e recuperar roteiro, acentos, orçamento por pessoa, cálculo de preços
no servidor, isolamento entre contas, recusa de orçamento insuficiente sem
gravação parcial e revogação de sessão no logout. As páginas JSP inicial,
listagem e sugestões responderam 200.

O teste cria duas contas com e-mails únicos em `example.invalid` e um rolê
fictício no banco isolado a cada execução. Esses dados permanecem para inspeção;
os tokens são revogados ao terminar e não são impressos.

A integração revelou que o Tomcat não descobria automaticamente o driver
MySQL do WAR. O gerenciador agora carrega explicitamente esse driver usando
o classloader da aplicação, mantendo H2 nos testes e o pool de conexões.

## Próximo passo: interface conectada

Esta validação é HTTP/backend, ainda não é um teste ponta a ponta do app.
A prévia Expo em outra porta precisa de uma política CORS restrita no servidor.
O backend ainda não a oferece. Não basta alterar a URL da prévia para afirmar
que o fluxo no navegador funciona. O teste em Android/iOS também está pendente;
a porta está restrita ao computador e não está publicada na rede Wi-Fi.

Para parar os serviços sem apagar os dados:

```powershell
docker compose -f compose.integration.yml stop
```
