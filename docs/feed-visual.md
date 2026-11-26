# Feed com fotos de capa

Entrega local de 26/11/2026, posterior ao APK `edb09f08-03fc-4647-abd9-b8b7303ef09a` validado pelo usuário.

## Fluxo

A aba Feed mostra autor, primeira foto da publicação, título, cidade, número de paradas/fotos e até duas linhas de relato. Tocar na capa ou em Conhecer passeio abre a publicação completa; tocar no autor abre seu perfil. Publicações sem fotos continuam disponíveis, sem imagem inventada.

Todos e Seguindo conservam a paginação de 20 itens. A lista renderiza os cartões por janela, em lotes pequenos. Puxar para atualizar retorna à primeira página; erros de conexão permitem tentar novamente. A falha de uma foto não bloqueia o acesso ao passeio.

## API e privacidade

- `GET /api/v1/comunidade/feed`: mantém o contrato anterior e adiciona `resumo` (até 180 caracteres) e `fotos` (quantidade). Não inclui imagens completas.
- `GET /api/v1/comunidade/capa/{publicacaoUUID}`: exige Bearer; retorna `{base64, legenda}`. JPEG de no máximo 480 pixels na maior dimensão e 64 KiB antes do Base64. A primeira foto é definida pela ordem salva na publicação.
- A consulta da capa verifica perfil público, publicação pública e ausência de bloqueio de moderação na mesma consulta SQL. Sem foto ou sem acesso: 404. Sem autenticação: 401. Processamento de imagens ocupado: 429, com nova tentativa pela interface.
- Respostas usam `Cache-Control: no-store`. A interface revalida ao voltar à tela/ao aplicativo; não persiste capas no armazenamento offline. Conteúdo já entregue não pode ser revogado instantaneamente de um aparelho: a revogação vale nas próximas consultas.
- A redução reutiliza o processamento limitado de JPEG/PNG, sem metadados originais. As fotos armazenadas não são substituídas pelas miniaturas. Até dois processamentos simultâneos por instância.
- Não exige migração: usa `publicacoes_fotos`, `publicacoes_roteiros`, `perfis_sociais` e `comunidade_bloqueios` existentes. Mantém Controller → Service → DAO.

## Testes no celular após disponibilizar a atualização

1. Publicar um roteiro com duas fotos e relato; conferir capa, quantidade e abertura das fotos completas em outra conta.
2. Conferir publicação sem foto e os filtros Todos/Seguindo.
3. Abrir próxima página, puxar para atualizar e conferir retorno ao início sem itens repetidos.
4. Ocultar publicação/perfil; voltar ao feed e confirmar remoção. Repetir após moderação com conta administradora.
5. Interromper a conexão, tentar abrir foto e recuperar com Tentar carregar foto, sem perder a navegação.
6. Conferir rolagem, recorte de fotos verticais, fonte ampliada e leitor de tela no Samsung.

Esta mudança ainda não foi incluída no APK instalado nem publicada no servidor online.

## Validação local concluída

- Compilação limpa: 188 testes Java aprovados, incluindo tamanho/dimensões da capa, preservação da foto original, resumo, privacidade do perfil/publicação, bloqueio de moderação, exclusão, paginação entre autores e autenticação/no-store do endpoint.
- 96 testes mobile aprovados; TypeScript e lint de `src/` aprovados. Exportação Android/Hermes concluída. Isso não substitui revisão visual no aparelho.
- Integração HTTP com MySQL/Tomcat: publicação com duas fotos, leitura do feed/capa, limite de tamanho, recusa sem sessão e revogação ao privatizar o perfil aprovados.
- Conta e roteiro temporários removidos; contagens locais preservadas: 32 contas, 12 roteiros e 25 rolês.
- A execução direta de lint sobre todo o diretório também encontrou dois alertas preexistentes de `Buffer` em um helper ignorado de `.expo/` e em `tests/offlineStore.test.mjs`; não são erros do código do aplicativo. O lint de `src/` foi executado separadamente e passou.
