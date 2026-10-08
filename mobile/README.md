# RolêOut Mobile — primeiro fluxo

App React Native + Expo + TypeScript, independente do WAR Java. Criado com o
template oficial blank-typescript; Expo SDK 57 e dependências compatíveis,
fixadas em `pnpm-lock.yaml`. Navegação por Expo Router em `src/app`. Referência visual: `../docs/referencia-figma.md`.

Entregue: marca com texto e pino vetorial (sem screenshot), login/cadastro, aba Criar rolê com cidade/categoria/orçamento como na web, Explorar com busca por cidade/categorias,
paginação, detalhes, conta e logout. Estados de carregamento, lista vazia,
erro de conexão e sessão inválida são separados. Na API real, nenhum dado fictício
é retornado como fallback de falha.

## Executar

Com Node e pnpm disponíveis:

```text
cd mobile
pnpm install --frozen-lockfile
pnpm start
```

Abrir no Expo Go compatível com SDK 57 ou numa development build correspondente.
O projeto ainda não foi testado num aparelho/emulador nem empacotado em APK/IPA.
Para preview de interface no navegador: `pnpm web`. A prévia web não representa
validação Android/iOS. O ambiente `compose.integration.yml` permite explicitamente
as origens `http://localhost:8081` e `http://127.0.0.1:8081`. Ver
`../docs/integracao-local.md` para iniciar a prévia conectada.

## API real

Copiar `.env.example` para `.env` e ajustar `EXPO_PUBLIC_API_URL`. Esse valor é
público no bundle e deve conter apenas a URL; não colocar senhas ou tokens nele.

- Emulador Android: `http://10.0.2.2:8080/RoleOut/api/v1`.
- Aparelho: endereço acessível do servidor na mesma rede, não localhost do aparelho.
- Produção: HTTPS obrigatório pelo cliente. HTTP é apenas para desenvolvimento
  local. Builds nativas também precisam permitir a rede de desenvolvimento.

O servidor deve usar V001/V002/V003/V004/V005, MySQL e Tomcat. Criar conta no app e fazer
login com os mesmos campos da API Java. Senha: 12 a 128 caracteres.

Token guardado com Expo SecureStore no Android/iOS, nunca com senha.
Na prévia web, token fica somente em memória e some ao recarregar. Token é
validado ao restaurar o app e ao abrir Conta. Logout revoga a sessão no servidor
e remove o token local; em falha de rede, informa erro para permitir nova tentativa.
Se o servidor já considera a sessão inválida, a saída remove o token local.
Persistência segura no aparelho ainda precisa de teste real.

## Demonstração

Em execução de desenvolvimento, “Conhecer a demonstração” abre quatro registros
fictícios, sem criar conta e sem chamar o banco. O aviso permanece visível na
listagem e nos detalhes. O atalho não aparece em builds de produção.

Fotos, avaliações e horários do Figma não foram simulados como informações
reais. Os cartões usam blocos de categoria até haver imagens disponíveis na API.
O login complementa o design; não foi identificada tela de login na faixa
principal inspecionada. As cores são aproximações visuais, não tokens exportados.

## Validação e próximos passos

TypeScript e lint passaram (`pnpm typecheck`, `pnpm lint -- --max-warnings 0`). Exportação Android/iOS/web passou: `pnpm exec expo export --platform all` (bundles, não APK/IPA).
Navegação verificada no navegador a 390×844: introdução -> login -> demonstração
-> detalhes -> volta; filtros de música e cidade sem resultados; conta -> saída.

Preferências opcionais persistidas e editáveis na conta: `../docs/preferencias.md`.
Roteiros com múltiplas paradas e persistência já estão implementados.
Ainda pendentes: validação completa pelas telas e Android/iOS, perfil completo,
uso das preferências nas recomendações, painel dos responsáveis e aprovação administrativa. Backend e web
continuam separados desta pasta; nenhum arquivo JSP foi substituído.

Referências: https://docs.expo.dev/versions/latest/sdk/securestore/
e o Figma indicado em `../docs/referencia-figma.md`.

## Mapa de proximidade (Android/iOS)

Explorar permite ativar a localização em primeiro plano e ver o mapa nativo.
A inscrição GPS é removida ao sair de Explorar; não há rastreamento em segundo
plano. O mapa consulta `/roles/proximos` com posição e raio de 5 km; a lista
por cidade é independente do mapa. A câmera pode ser centralizada manualmente.
Permissão recusada e GPS indisponível exibem mensagem e mantêm a busca disponível.

Somente rolês vinculados a locais PUBLICADO e organizações APROVADA, com latitude
 e longitude cadastradas, aparecem como pontos. Os exemplos antigos sem local_id
não ganham coordenadas inventadas. Máximo de 100 pontos por consulta.
A API não persiste a posição; servidor/proxy devem evitar registrar coordenadas
em logs de URLs antes da publicação. HTTPS continua obrigatório em produção.

O mapa nativo ainda precisa de validação em aparelho, incluindo permissão, GPS,
pontos e consumo de bateria. A prévia web não contém mapa; não representa teste
nativo. Para distribuir Android, configurar chave Google Maps restrita ao app,
conforme https://docs.expo.dev/versions/v57.0.0/sdk/map-view/ . Não há chave
embutida neste projeto. Testar no Expo Go compatível ou development build.

A aba Criar rolê agora tem duas etapas e montagem manual de várias paradas.
Data, participantes, orçamento total, ordem e duração estimada são considerados.
A montagem fica em memória até salvar; roteiros salvos podem ser recuperados em Conta -> Meus roteiros. Consulte ../docs/roteiro-mobile.md.

Testes de regras: pnpm test (Node 24). Ainda pendente: preferências
de ambientes, trajetos, horários reais e integração no aparelho.

Persistência e contrato: ../docs/roteiros-salvos.md. Requer sessão real e V004.
Demonstração não grava no servidor. A integração real no aparelho continua pendente.
