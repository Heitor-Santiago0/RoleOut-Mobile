# Perfis públicos — primeira entrega social

Atualização de 19/11/2026: [publicação de roteiros com fotos e relatos](publicacoes-roteiros.md) já implementada localmente. O restante desta página registra a entrega original de perfis.

Implementação local de 22/10/2026. A publicação de fotos e roteiros, feed, comentários, seguir pessoas e Copiar rolê serão entregas posteriores. Este fluxo estabelece a identidade pública com privacidade explícita.

## Fluxo no aplicativo

Perfil → Meu perfil público permite escolher um nome independente do nome da conta (até 80 caracteres), apresentação opcional (até 500) e visibilidade. O perfil começa privado e vazio. Nenhum dado é copiado automaticamente do cadastro.

Salvar perfil público torna esses dois textos visíveis para outras contas autenticadas. Ver como outras pessoas abre a consulta pública real. Tornar privado agora oculta o perfil usando a última versão salva, sem publicar alterações ainda não salvas no formulário. Conhecer pessoas lista somente perfis públicos, com páginas de até 20 e acesso ao detalhe.

A navegação inferior e a seta de voltar existentes continuam disponíveis. O editor utiliza o tratamento de teclado atual. Falha de conexão mantém o texto digitado para nova tentativa; conflito exige recarregar a versão salva. Conteúdo público é consultado novamente ao abrir/retomar a tela e não tem cópia offline. Tornar privado impede novas consultas; não apaga conteúdo já visto por outra pessoa. Uma tela pública já aberta será atualizada ao ser retomada ou recarregada.

## Dados e privacidade

V027 adiciona somente `perfis_sociais`, com referência à conta, identificador público UUID, textos, visibilidade e versão. Não migra contas para público nem modifica roteiros, preferências, locais ou eventos. O identificador da conta não é retornado no perfil público.

Os endpoints públicos dentro do app retornam somente `id`, `nome` e `apresentacao`. E-mail, senha, localização, interesses e roteiros não são consultados por eles. Perfil privado e inexistente retornam o mesmo 404. A privacidade é verificada no servidor a cada consulta; todas as respostas usam `Cache-Control: no-store`.

Gravações obtêm o dono da sessão autenticada e rejeitam campos desconhecidos, inclusive contaId. A versão esperada e o bloqueio da conta serializam alterações: uma tela antiga não consegue republicar um perfil ocultado em outra. UUID permanece estável ao ocultar/publicar novamente.

## API

- GET `/perfil-social`: perfil da sessão; sem cadastro retorna id null, textos vazios, publico false e versao 0.
- PUT `/perfil-social`: `{nome, apresentacao, publico, versao}`. Retorna perfil salvo e versão incrementada; 409 quando desatualizado.
- GET `/perfis?apos={UUID}`: `{itens, proximo}`. Cursor ordenado por UUID, até 20 resultados, proximo null no fim.
- GET `/perfis/{UUID}`: nome e apresentação de perfil visível.

Todos exigem sessão válida. Contrato registrado em `openapi.yaml`.

## Validação local

- 153 testes Java aprovados, incluindo dez novos de serviço/API: privado inicial, campos públicos, isolamento, ocultação, conflito, concorrência, limites, paginação e autenticação.
- 90 testes móveis aprovados, incluindo validação de formulário e exclusão de dados privados do payload. TypeScript e lint aprovados.
- Teste HTTP real contra MySQL/Tomcat com duas contas temporárias: criar, publicar, consultar com outra conta, listar, ocultar e impedir sobrescrita desatualizada. Contas temporárias removidas ao terminar.
- Migração aplicada no MySQL local e API atualizada. Totais finais preservados: 32 contas, 12 roteiros e 22 rolês; nenhum perfil público residual de teste. Nenhuma hospedagem ou nova dependência nativa.
- Bundle Android exportado com sucesso (1583 módulos), sem precisar gerar outro APK para estas telas.

## Celular — testes adiados a pedido do usuário

1. Abrir Meu perfil público e confirmar estado privado inicialmente, sem copiar nome ou e-mail.
2. Preencher nome e apresentação, conferir teclado/rolagem, salvar privado e reabrir.
3. Publicar, visualizar como outra pessoa e encontrar o perfil em Conhecer pessoas.
4. Consultar em uma segunda conta; confirmar que aparecem somente nome e apresentação.
5. Tornar privado, reabrir lista/detalhe na segunda conta e confirmar que ficou indisponível.
6. Testar falta de conexão durante salvamento: não deve afirmar sucesso nem apagar o texto.
7. Testar mudança concorrente em duas sessões: exibir conflito e permitir recarregar.

Os testes de permissões e avisos de viagens também continuam pendentes em `avisos-viagens.md`. Não considerar esta entrega como homologada no dispositivo.
