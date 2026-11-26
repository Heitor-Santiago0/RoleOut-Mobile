# Comunidade e cópia de roteiros

Entrega local de 25/11/2026. Banco, API e aplicativo implementados; validação física está em [Checklist da entrega](entrega-final-t2.md). Esta frente complementa os requisitos do T2, sem substituir seu CRUD Java.

## Usar no aplicativo

- **Perfil → Comunidade:** passeios públicos, com páginas de 20 itens; Todos ou Acompanhando. A ordenação é pelo identificador de criação do roteiro, não por popularidade nem pela data da última publicação. Cada cartão abre fotos, relato e paradas.
- **Perfil público de alguém → Acompanhar passeios:** incluir ou remover essa pessoa do filtro. A lista de pessoas acompanhadas não é pública.
- **Passeio publicado → Comentários:** escrever ou atualizar um comentário por pessoa/publicação. O autor pode excluir seu próprio comentário. Exige perfil público; ocultar o perfil retira seus comentários e avaliações da consulta pública.
- **Local → Avaliações de visitantes:** nota de 1 a 5, relato e média das avaliações visíveis. É necessário ter marcado a parada como concluída em roteiro de hoje ou de data passada. Essa visita é **autodeclarada**, não uma prova de presença por GPS, ingresso ou pagamento. Uma avaliação por conta/atividade, editável.
- **Denunciar:** explicar o problema de uma publicação, comentário ou avaliação. A identidade de quem denuncia não aparece ao autor. Denúncias repetidas pela mesma conta e conteúdo são idempotentes.
- **Perfil de administrador → Analisar denúncias:** ler o conteúdo/motivo, registrar justificativa e manter ou ocultar. Decisões repetidas são recusadas; a API confere o papel administrativo. Conteúdo ocultado não aparece no feed, comentários ou média; publicação ocultada também não pode ser copiada. Editar um comentário ocultado não o torna visível novamente.

Comentários, fotos e relatos são voluntariamente públicos para outras contas autenticadas. E-mail, participantes, orçamento e horários privados do roteiro não são publicados automaticamente. Contatos podem ser compartilhados voluntariamente na apresentação pública ou em um comentário; não há mensagens privadas ou extração de contatos da agenda.

## Copiar rolê

No passeio publicado, toque **Copiar rolê**. Informe nome, nova data, partida, duração total, participantes e orçamento do grupo. Reordene, remova, ajuste a duração/horário de cada visita e adicione outras paradas da mesma cidade. É necessário manter ao menos uma parada do passeio original.

**Verificar e salvar minha cópia** chama a mesma regra de negócio usada pelo CRUD de roteiros. O servidor confere novamente aprovação, cancelamento, cidade, datas, início/término, funcionamento, duração e preços atuais. Uma falha não grava roteiro parcial nem créditos isolados. A nova cópia é privada; progresso, fotos, relato e planejamento privado do autor não são copiados. Os créditos são calculados pelo servidor, nunca aceitos do cliente.

Os créditos incluem o nome público/título na ocasião da cópia. Permanecem no roteiro salvo mesmo se o original for removido ou ocultado; o conteúdo original deixa de estar acessível. Uma cópia de outra cópia conserva os créditos da origem registrada. Se a cópia for publicada, a tela pública também mostra esses créditos. A consulta offline do planejamento continua disponível, mas a consulta separada dos créditos exige conexão.

Não há reserva ou garantia de vagas. Não inventamos tempo de deslocamento: o roteiro informa que esses tempos não estão incluídos. Falha de conexão depois do envio pode deixar incerto se uma criação foi salva; confira Meus rolês antes de repetir.

## Banco e camadas

V029 cria `comunidade_textos`, `comunidade_seguidos`, `comunidade_denuncias`, `comunidade_bloqueios` e `roteiro_creditos`. Não altera registros anteriores. Aplicar uma vez após V028 em bancos existentes, sem recriar volume.

- Controller: `CommunityServlet`, autenticação e contratos HTTP.
- Service: `CommunityService`, privacidade, regras de participação e moderação; `ItineraryService.copy`, validação transacional da cópia.
- DAO: `CommunityDAO`, `PublicationDAO` e `ItineraryDAO`, SQL parametrizado e persistência.
- Model: `CommunityData` e modelos existentes.

## Contratos HTTP

Base `/api/v1`; todas as rotas exigem sessão Bearer e usam respostas `Cache-Control: no-store`. Corpos JSON rejeitam campos desconhecidos. Erros seguem `codigo` e `mensagem`.

| Método e caminho | Entrada / resultado |
| --- | --- |
| GET `/comunidade/feed?seguindo=false&apos=123` | `itens`, `proximo`; omitir `apos` na primeira página |
| GET `/comunidade/seguir/{perfilUUID}` | `{seguindo: boolean}` |
| PUT / DELETE `/comunidade/seguir/{perfilUUID}` | Acompanhar / parar; 204 |
| GET `/comunidade/textos/{tipo}/{alvo}?apos=0` | Tipos COMENTARIO/publicação UUID e AVALIACAO/rolê ID; `itens`, `proximo`, `total`, `media` |
| PUT `/comunidade/textos/{tipo}/{alvo}` | `{texto,nota}`; até 1.000 caracteres, nota nula para comentário; 204 |
| DELETE `/comunidade/textos/{tipo}/{alvo}` | Remove somente o próprio texto visível; 204 |
| POST `/comunidade/denuncias` | `{tipo,alvo,motivo}`; PUBLICACAO/UUID ou TEXTO/ID; 204 |
| GET `/comunidade/denuncias?apos=0` | Administrador: até 20 pendentes, `itens`; próxima página usa último ID |
| PUT `/comunidade/denuncias/{id}` | Administrador: `{ocultar,justificativa}`; 204 |
| POST `/comunidade/copiar/{publicacaoUUID}` | `{planejamento: <entrada do POST /roteiros>}`; 201, `roteiro` e `paradas` |
| GET `/comunidade/creditos/{roteiroId}` | Somente proprietário: `creditos` |
| GET `/comunidade/origem/{publicacaoUUID}` | Créditos de publicação visível: `creditos` |

401 sem sessão; 403 moderação sem papel; 404 conteúdo indisponível; 400 formato/horários inválidos; 409 perfil privado, visita não declarada, decisão repetida ou conflito de preço/disponibilidade.

## Validação e limites

`CommunityServiceTest` cobre privacidade, acompanhamento, autoria de textos, avaliações após conclusão, cópia/revalidação, crédito herdado, exclusão do original e denúncias/moderação. `CommunityServletTest` cobre autenticação, recusa de autor enviado pelo cliente e não armazenamento em cache. `copyItinerary.test.mjs` cobre entrada da cópia, campos permitidos e erros antes do envio.

`node scripts/test-community.mjs` executa o fluxo HTTP completo com MySQL real, contas/atividade temporárias e limpeza dos próprios registros. Execute cada script de integração isoladamente; várias rodadas seguidas podem atingir o limitador de cadastro. Reiniciar apenas o Tomcat de testes limpa esse limitador, sem apagar o banco.

Esta é uma comunidade para homologação local. Antes de uso público são necessários operação contínua de moderação, mecanismos adicionais contra abuso/recriação de conteúdo, bloqueio de usuários, recursos de revisão de decisões, hospedagem HTTPS, backups e análise de carga. Não há mensagens privadas, reservas, lotação em tempo real nem navegação curva a curva própria. Esses itens não são requisitos do PDF T2.
