# Perfil, feed e catálogo de Uberaba

Entrega de 26/11/2026.

- Cadastro e login aceitam senhas de 6 a 128 caracteres. Hash e limites de tentativas mantidos.
- Navegação principal: Explorar, Feed, Criar rolê e Perfil. Feed reúne somente publicações e perfis públicos, com filtro Seguindo e atualização ao puxar a lista.
- Perfil com foto, nome, bio, quantidade de publicações públicas (exclui conteúdo moderado), seguidores e seguindo. Foto opcional, validada e reprocessada no servidor para remover metadados. Perfil privado continua inacessível a terceiros.
- Compartilhamento de interesses desativado por padrão. A pessoa escolhe se quer exibi-los. Apenas seleções atuais são apresentadas; e-mail, localização e roteiros privados não são expostos.
- Configurações concentra conta, interesses, permissões, avisos e administração. Detalhes de funcionamento ficam em seção expansível.
- Seguir pessoas continua usando o fluxo existente; seguir locais é persistido por conta e estabelecimento, com opção de deixar de seguir e lista no perfil. Seguir não faz reserva nem ativa alertas novos automaticamente.
- Vocabulário ampliado de música, culinária, atividades e ambiente, com seis etapas opcionais para interesses. Categorias compartilhadas entre descoberta e criação de roteiro.
- Três locais públicos reais com fotos licenciadas: veja [fontes e créditos](fotos-uberaba.md). Catálogo editorial não representa os gestores dos locais.

## Banco e API

Aplicar V030 uma única vez no banco existente antes do servidor novo. Migração aditiva, sem apagar registros. Executar `scripts/seed-uberaba.sql` com UTF-8; é idempotente.

Contrato estruturado: [OpenAPI](openapi-perfil-locais.json). Todos os endpoints exigem sessão:

| Método e caminho | Uso |
|---|---|
| GET /perfil-detalhes | Foto, privacidade dos interesses e contadores próprios |
| PUT /perfil-detalhes | `{fotoBase64: string ou null, interessesPublicos: boolean}`; null remove foto |
| GET /perfil-detalhes/{UUID público} | Detalhes de perfil público; privado/inexistente retorna 404 |
| GET /locais-seguidos | Locais seguidos pela conta, até 100 |
| GET /locais-seguidos/{id do rolê} | Estado do estabelecimento associado |
| PUT /locais-seguidos/{id do rolê} | Seguir; operação idempotente |
| DELETE /locais-seguidos/{id do rolê} | Deixar de seguir |

Locais suspensos e organizações não aprovadas não são retornados. Rascunhos legados sem local associado não oferecem seguir local. Fotos aceitam JPEG/PNG de até 2 MB; a interface reduz a foto de perfil antes de enviar. A edição salva perfil e detalhes em duas operações; falha parcial é informada e permite tentar novamente sem duplicar perfil.

## Validação

- Compilação limpa e 184 testes Java aprovados (incluem limites de senha, privacidade, contadores e seguir local).
- Migração e seed local: 32 contas e 12 roteiros mantidos; 22 para 25 rolês, sem duplicação ao reaplicar seed.
- Verificação de tipos e lint aprovados; 96 testes mobile aprovados. Teste HTTP local validou foto, interesses privados/públicos, senha curta, seguidores e isolamento entre contas.

## Validação no celular da atualização

Em 26/11/2026, após instalar a atualização e receber o roteiro de testes, o usuário informou: “Testei no celular e está tudo funcionando”. Registramos a confirmação geral desta entrega. Isso não comprova individualmente os testes antigos de viagem, moderação ou notificações que não foram relatados separadamente.

Roteiro fornecido para esta entrega:

1. Criar conta com senha de seis caracteres; senha com cinco deve ser recusada.
2. Abrir Feed, alternar Todos/Seguindo, abrir autor e seguir/deixar de seguir; conferir em outra conta.
3. Editar foto e bio, publicar/privatizar perfil; confirmar que perfil privado desaparece para terceiros.
4. Ativar/desativar interesses públicos e conferir em outra conta; garantir que os interesses permanecem salvos para recomendações.
5. Seguir um local real, encontrá-lo em Perfil > Locais que sigo e deixar de seguir.
6. Percorrer as seis perguntas, reabrir e conferir persistência; filtrar novas categorias.
7. Conferir mapa, fotos, créditos e detalhes dos três locais; horários não devem ser anunciados como confirmados.
8. Verificar tamanho de fonte, foto, navegação e teclado no Samsung; salvar e abrir roteiros antigos.

## Publicação e verificações online

Servidor publicado em 26/11/2026 (deploy `882da127-7a5d-4a37-ae1e-964a93cf9ab2`). Migração manteve 33 contas e 12 roteiros online, com 25 rolês após adicionar os três locais. Duas contas temporárias do teste foram removidas; o serviço temporário de migração também foi removido. Entec e plano de cobrança não foram alterados.

Teste HTTP online aprovado: cadastro/login com seis caracteres, foto reprocessada, opt-in e revogação dos interesses, seguidores, seguir/deixar de seguir estabelecimento, idempotência e isolamento por conta. Revisão visual em prévia web de 390 × 844: perfil, Feed, editor, catálogo com foto real e seguir local conferidos. A prévia web não substitui a validação nativa de galeria, mapa e teclado no Samsung.

APK concluído: [instalar](https://expo.dev/artifacts/eas/XX8VC7CkII78NO9U-_QUMotzdODc8lvOroW7gohmPE4.apk) · [build FINISHED](https://expo.dev/accounts/toledosantiago0/projects/roleout-mobile/builds/edb09f08-03fc-4647-abd9-b8b7303ef09a). Tamanho: 136,8 MB. SHA-256 `afac9d1b4024df3054cc7b0db9ae0649d3e909bfa7b539187e210480aa1ca9f9`. Código mobile do commit `68acebc`; commits seguintes desta entrega alteram documentação e seed. Pacote Android, CRC, novos endpoints, fotografias e URL de produção conferidos.
