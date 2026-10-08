# Escopo do MVP acadêmico

Objetivo: evoluir o RolêOut para API Java + app React Native/Expo/TypeScript,
preservando a versão web. Manter incrementos pequenos e demonstráveis no TCC.

## Base necessária

- Pool de conexões com Singleton do gerenciador, sem Connection compartilhada.
- API REST para consultas e cadastro, reaproveitando o banco e a lógica existente.
- Login, armazenamento seguro de senhas, sessão expirada/revogável.
- Separação entre cadastros de organizações diferentes.
- Aprovação administrativa antes da publicação de organizações e locais.
- Testes relevantes e integração real MySQL/Tomcat antes de declarar a web validada.

A inclusão de organizações/locais e da aprovação foi solicitada e confirmada
pelo usuário. Ela amplia o domínio original, mas faz parte do produto desejado.

## Implementações antecipadas

O papel de editor, suporte a várias sessões e controles operacionais foram
introduzidos antes do primeiro fluxo visual. Permanecem na base já testada,
mas não justificam construir agora telas de equipes, gestão de sessões ou
infraestrutura distribuída. Não são dependências para começar o app.

## Sequência curta

1. Consolidar a base atual: código local versionado, 28 testes aprovados,
   limitações documentadas. Validação MySQL/Tomcat ainda pendente.
2. Recuperar as referências visuais Figma e criar o app numa pasta `mobile/`,
   separada do WAR. Primeiro fluxo demonstrável: login -> Explorar -> detalhes,
   com estados de carregamento, lista vazia, sessão expirada e erro de conexão.
   Dados de teste serão identificados como demonstração. Simulações durante
   desenvolvimento não contam como integração real com a API.
3. Acrescentar onboarding opcional, perfil e um primeiro roteiro personalizado
   com regras simples, explicáveis e testáveis; não requer serviço de IA externo.
4. Completar o fluxo dos responsáveis: formulário -> rascunho -> envio ->
   análise por administrador -> publicação. O app pode ser desenvolvido enquanto
   esse fluxo está sendo completado; local sem aprovação não é divulgado.
5. Integrar tudo em MySQL/Tomcat e aparelho/emulador, corrigir falhas, revisar
   segurança da administração web e preparar a demonstração acadêmica.

O onboarding e os roteiros continuam no escopo original. A ordem acima prioriza
uma pequena experiência funcionando antes de expandir as telas.

## Depois do primeiro fluxo completo

Fotos/uploads, convites de equipe, gestão de múltiplos editores, horários
detalhados, mapas avançados, notificações e métricas de produção podem esperar.
Confirmação de e-mail e recuperação de senha podem ser desenvolvidas depois
do protótipo local, mas devem preceder abertura do cadastro ao público real.
Nenhum envio real de e-mail ou publicação externa foi configurado nesta etapa.

Não introduzir microserviços, cache distribuído, filas ou infraestrutura de
alta disponibilidade sem um problema medido que exija isso.

## Limitações atuais

O primeiro app em `mobile/` já implementa introdução, login/cadastro, Explorar,
detalhes e conta. A demonstração foi verificada no navegador; ainda não houve
integração real API/aparelho. Não há painel de responsáveis ou aprovação administrativa.
Há APIs de consulta, login e rascunhos. Os testes são H2 e servlets simulados,
não testes reais MySQL/Tomcat nem garantia de capacidade sob carga.
Login novo não protege automaticamente os controllers da web legada.
O link Figma foi recebido e visualizado; observações em `referencia-figma.md`.
Assets e tokens exatos não foram exportados.
