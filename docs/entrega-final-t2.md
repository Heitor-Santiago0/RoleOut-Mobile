# Entrega T2 e rodada de validação

Consolidação de 25/11/2026. Prazo no PDF original: **26/11/2026**.

## O que o professor exige

| Requisito do PDF | Onde demonstrar |
| --- | --- |
| API RESTful completa em Java | Java 17, Jakarta Servlet, `/api/v1/roteiros` |
| MVC com Controller, Service, DAO | `ItineraryServlet` → `ItineraryService` → `ItineraryDAO`; dados em `ItineraryData`; aplicativo é a View |
| CRUD de uma entidade do tema | Criar, consultar, renomear e excluir Roteiro; demonstrar POST, GET, PUT e DELETE |
| MySQL | Compose local com MySQL 8.4 e migrações V001–V029 |
| Organização e boas práticas | Camadas, SQL parametrizado, autorização por conta, transações e testes |
| Documentar endpoints | [OpenAPI](openapi.yaml), [guia T2](trabalho-api-t2.md), [comunidade](comunidade.md) |
| Projeto no GitHub | Branch `develop/api-pool`; conferir commit remoto da entrega |
| Link no disco virtual do AVA | **Pendente: ação da dupla** |
| Apresentação e contribuição individual | **Pendente: cada integrante explica sua contribuição real** |

O PDF não exige Firebase, hospedagem, Google Maps, rede social ou publicação em loja. A interface TypeScript não muda o fato de que a API do trabalho é Java. JSON é o formato da comunicação, não uma linguagem de implementação do backend.

## Apresentação em cinco passos

1. Explicar o problema: descobrir lazer e organizar um passeio compatível com tempo/orçamento.
2. Mostrar as quatro classes do CRUD e o caminho requisição → regra → banco → resposta JSON.
3. Demonstrar criar roteiro, consultar, renomear e excluir. Usar uma conta de demonstração; não exibir tokens ou senhas em slides/GitHub.
4. Mostrar persistência MySQL e executar `node scripts/test-t2-crud.mjs`. O script cria e remove apenas seus registros temporários, sem depender do celular.
5. Mostrar o aplicativo e explicar limites: ambiente local, dados fictícios identificados, sem reserva garantida e validação física separada dos testes automáticos.

## Preparar o notebook

Na raiz do repositório, com JDK 17, Maven, Node.js e Docker Desktop:

```sh
mvn -B verify
docker compose -f compose.integration.yml up -d
node scripts/test-t2-crud.mjs
```

Em banco novo, o Compose aplica as migrações. Em banco existente, aplicar somente as faltantes; nunca apagar o volume para atualizar. Após recompilar a API, reiniciar o Tomcat. O teste HTTP usa `127.0.0.1:8082/RoleOut/api/v1`.

No aplicativo, `pnpm run typecheck`, `pnpm run lint` e `pnpm test`. O APK de desenvolvimento exige servidor Expo ativo para abrir o código. No USB, conferir `adb devices`, configurar `adb reverse tcp:8084 tcp:8084` e a porta da API usada no `.env.local`, e iniciar Expo em 8084. A API do Compose usa 8082; o endereço precisa terminar em `/RoleOut/api/v1`. Não presumir que o encaminhamento sobrevive a desconectar o cabo.

## Uma rodada no celular — ainda não realizada

Use o novo APK com suporte a fotos e duas contas de teste. A instalação não é validação por si só. Execute em sequência:

- [ ] Abrir pelo USB; login; fechar/reabrir; conferir que erro de conexão oferece recuperação.
- [ ] Explorar: mapa, posição, locais, categorias e recomendações. Negar localização deve manter consulta por cidade utilizável.
- [ ] Criar roteiro com duas paradas; ajustar ordem, duração e horário; salvar. Criar outro com data diferente e verificar separação.
- [ ] Renomear; concluir parada; baixar offline; desconectar rede e consultar o roteiro baixado; reconectar.
- [ ] Publicar perfil e roteiro com três fotos/legendas e relato; prévia; salvar; editar; conferir a segunda conta.
- [ ] Comunidade: abrir publicação, acompanhar autor, filtrar Acompanhando, comentar/editar/excluir.
- [ ] Copiar rolê para nova data/orçamento; mudar paradas e salvar. Confirmar que o original não mudou e que a cópia mostra créditos.
- [ ] Tentar copiar evento cancelado, data incompatível e orçamento insuficiente: receber mensagem, sem cópia parcial.
- [ ] Avaliar uma parada concluída de hoje/data passada; conferir nota e média com outra conta. Parada não concluída deve ser recusada.
- [ ] Denunciar comentário/publicação; com administrador analisar e ocultar; verificar que sumiu para outra conta e não pode ser copiada.
- [ ] Ocultar perfil/publicação; verificar que não aparece na comunidade, inclusive após sair e voltar à tela.
- [ ] Navegação: setas em telas secundárias, sem seta no Explorar; abas acessíveis; teclado não cobre campos de data/relato.
- [ ] Permissões: autorizar/recusar; configuração reflete o estado real; não solicitar repetidamente sem necessidade.
- [ ] Aviso de horário com app em segundo plano; viagem com serviço autorizado. A API local precisa estar acessível; viagem real fora da rede depende de hospedagem futura.

Se algum item falhar, registrar a tela, a ação e a mensagem para corrigir antes da apresentação. Não é possível garantir perfeição sem esta validação; a rodada foi agrupada para reduzir o trabalho repetido.

## Fora da entrega acadêmica local

Hospedagem pública/HTTPS, operação contínua do servidor, recuperação de senha por e-mail, backups de produção, distribuição independente do notebook, validação iOS e políticas/controles adicionais para uma comunidade aberta. Não são apresentados como concluídos.


## Validação consolidada — 26/11/2026

- 179 testes Java aprovados, incluindo autenticação, arquitetura e regras sociais.
- 95 testes móveis aprovados; TypeScript e lint sem erros; bundle Android exportado (1.608 módulos).
- CRUD T2, publicação com foto e comunidade/cópia aprovados por HTTP com MySQL real.
- Mantidos 32 cadastros, 12 roteiros e 22 rolês; registros temporários dos testes removidos.
- Testes no aparelho ainda não realizados nesta consolidação. O checklist acima não foi marcado como concluído.

## APK e GitHub verificados — 26/11/2026

- [APK de desenvolvimento atualizado](https://expo.dev/artifacts/eas/UkswMJk2xHcaOxRCV4dLwohNFXOUYbkC29JBeAhgVvs.apk).
- [Build concluído no Expo](https://expo.dev/accounts/toledosantiago0/projects/roleout-mobile/builds/f17b7092-1f0b-4dda-9a84-73e77410903e), status FINISHED. Arquivo local ignorado pelo Git: `mobile/.expo/apks/roleout-t2-2026-11-26.apk`.
- Integridade ZIP, pacote `br.com.roleout.mobile`, permissões de notificações/localização e módulos nativos de seleção/manipulação de fotos conferidos. Isso não substitui instalação e teste físico.
- SHA-256: `b89b495cd42e57530128baeb78fa42f03b88e9debc21174af0df92f511a82c74`.
- Build enviado a partir da árvore de trabalho antes do commit; o rótulo do Expo ainda referencia o commit anterior. As alterações foram consolidadas e enviadas ao GitHub no commit `5b4d43d`.
- Continua sendo APK de desenvolvimento: necessita Expo/Metro e API do notebook acessíveis. Não é uma versão autônoma hospedada.
- Repositório confirmado **privado**; o professor precisa ser autorizado como colaborador ou o proprietário precisa autorizar mudança para público. A visibilidade não foi alterada automaticamente. A branch com esta entrega é `develop/api-pool`, não a `main` antiga.
- Revisão dos 55 arquivos preparados e de 544 objetos históricos de texto não encontrou os padrões de chaves privadas/tokens verificados. Credenciais, configurações locais e APK não foram enviados ao GitHub.
