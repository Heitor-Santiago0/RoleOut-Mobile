# Publicação de roteiros com fotos e relatos

Entrega local de 19/11/2026. Reutiliza a arquitetura Controller → Service → DAO e os perfis públicos. Feed geral, comentários, seguir pessoas, avaliações e Copiar rolê continuam como próximas entregas.

## Fluxo

1. Meus rolês → abrir um roteiro → **Compartilhar passeio**.
2. Escolher um título de até 120 caracteres e escrever relato de até 3.000. Adicionar de zero a três fotos, com legenda opcional de até 200 caracteres; fotos podem ser removidas antes de salvar.
3. **Salvar como rascunho privado** guarda o conteúdo somente para a conta. Se a publicação já estava pública, essa opção também a torna privada.
4. **Revisar antes de publicar** mostra o conteúdo que será compartilhado. **Confirmar publicação** exige perfil público e torna o passeio acessível a pessoas autenticadas, na lista do perfil do autor.
5. O mesmo botão do roteiro abre a publicação existente para editar. Alterações só são públicas após a confirmação. **Ocultar publicação agora** altera somente a visibilidade salva, sem publicar textos/fotos ainda em edição.
6. **Excluir publicação** requer confirmação e remove relato e fotos; preserva o roteiro privado. Excluir o roteiro original também remove a publicação, comportamento informado na confirmação de exclusão.

As telas mantêm a navegação inferior e a seta de voltar. Formulários usam o tratamento de teclado existente. Os rascunhos não são salvos automaticamente: salvar antes de sair. Falha de rede mantém o formulário na tela; conflito exige recarregar a versão salva. Se uma requisição terminar no servidor após timeout, a versão impede que uma repetição silenciosamente crie outra publicação.

## Privacidade e dados compartilhados

Publicação é opt-in e começa privada, sem criar registros só por abrir o editor. Uma publicação por roteiro; limite de 20 por conta, incluindo rascunhos. Nenhum roteiro existente foi publicado automaticamente.

Compartilhados: título escolhido, relato, cidade, nomes/locais das paradas, duração planejada por parada, fotos/legendas e identidade pública do autor. Não compartilhados automaticamente: ID da conta, ID do roteiro privado, e-mail, data de saída, horários, participantes, orçamento, preços salvos, progresso ou localização GPS do usuário. Os textos e imagens escolhidos podem conter informações pessoais; a prévia permite revisá-los antes da confirmação.

As paradas são copiadas do roteiro do próprio usuário pelo servidor ao criar o primeiro rascunho. O cliente não pode enviar paradas, cidade ou autor arbitrários. A cópia permanece estável se o planejamento privado mudar. Para registrar outro conjunto de paradas, exclua a publicação e crie novamente. Publicar não altera o roteiro privado nem o cadastro dos locais e não confirma reserva, funcionamento ou preço atual.

Toda leitura pública exige sessão, publicação pública e perfil público. Ocultar o perfil bloqueia também lista e detalhe dos passeios; republicar o perfil volta a mostrar as publicações que continuam marcadas como públicas. Conteúdo já visto ou capturado por outra pessoa não pode ser revogado. As telas consultam novamente ao abrir/retomar; em erro não mantêm a última publicação pública visível. Não há cache offline das publicações.

## Fotos

Usa `expo-image-picker` e `expo-image-manipulator` compatíveis com Expo SDK 57. O seletor do sistema abre somente por ação do usuário, para imagens da galeria. Não solicita câmera ou microfone. Fotos são reduzidas no cliente e enviadas como Base64 em JSON autenticado, sem URLs externas ou uploads em nuvem.

O servidor aceita até três imagens JPEG/PNG, de até 2 MiB cada, com limite de dimensões/pixels antes da decodificação completa. Reamostra e reencoda em JPEG de até 1280 pixels no maior lado, descartando metadados originais (incluindo EXIF/GPS). Não aceita SVG, URLs, nomes de arquivo ou conteúdo não reconhecido como imagem. Duas imagens podem ser processadas simultaneamente pelo servidor; excesso retorna 429. Corpo JSON limitado a 8.500.000 bytes/caracteres. Upload no app espera até 60 segundos.

Neste ambiente local, fotos ficam em BLOBs no MySQL, vinculadas à publicação e excluídas em cascata com ela. Listas retornam somente resumo e contagem; fotos são carregadas no detalhe autenticado. Isso mantém a entrega independente de hospedagem. Antes de uso público em escala, avaliar armazenamento de objetos, miniaturas, quotas operacionais e moderação. Denúncias/moderação social ainda não foram implementadas; não disponibilizar este fluxo como serviço público final sem essa etapa.

Referências usadas: [ImagePicker SDK 57](https://docs.expo.dev/versions/v57.0.0/sdk/imagepicker/) e [ImageManipulator SDK 57](https://docs.expo.dev/versions/v57.0.0/sdk/imagemanipulator/).

## Arquitetura e banco

- `PublicationServlet`: autenticação, métodos, limites do corpo e JSON.
- `PublicationService`: propriedade, visibilidade, versão, limites e transação.
- `PublicationPhotos`: validação/redução de imagens e remoção de metadados.
- `PublicationDAO`: persistência, leituras filtradas e mapeamento.
- `PublicationData`: contratos sem dependência de transporte ou persistência.
- V028 cria `publicacoes_roteiros` e `publicacoes_fotos`; não altera registros anteriores.

Salvamento bloqueia conta e roteiro na mesma ordem dos fluxos existentes. Verifica versão e UUID: uma tela antiga não pode republicar conteúdo ocultado, nem sobrescrever uma publicação excluída e recriada com outra identidade. Gravação de relato, snapshot e fotos é atômica. A privacidade do perfil usa o mesmo bloqueio de conta.

## API

Base local: `http://127.0.0.1:8082/RoleOut/api/v1`. Todos os endpoints exigem Bearer token; respostas `Cache-Control: no-store`.

| Método e rota | Função |
| --- | --- |
| GET `/minhas-publicacoes/{roteiroId}` | Ler editor próprio; se ainda não há publicação, retorna `id:null`, `publico:false`, `versao:0`, título/cidade/paradas do roteiro e fotos vazias |
| PUT `/minhas-publicacoes/{roteiroId}` | Criar/editar rascunho ou publicar explicitamente |
| PUT `/minhas-publicacoes/{roteiroId}/ocultar` | Ocultar a versão salva sem modificar conteúdo |
| DELETE `/minhas-publicacoes/{roteiroId}` | Excluir publicação/fotos; preserva roteiro privado |
| GET `/publicacoes?autor={UUID}&apos={UUID}` | Lista por autor público, até 20 resultados, `proximo:null` no fim; cursor opcional |
| GET `/publicacoes/{UUID}` | Conteúdo público e fotos; sem dados do planejamento privado |

PUT de criação/edição:

```json
{
  "id": null,
  "titulo": "Museu e café no centro",
  "relato": "O que mais gostei foi…",
  "publico": false,
  "versao": 0,
  "fotos": [{"base64": "BASE64_JPEG_OU_PNG", "legenda": "Pausa para um café"}]
}
```

Na edição use `id` e `versao` retornados pelo servidor. Fotos opcionais: envie `[]` para nenhuma foto. A lista enviada substitui integralmente as fotos anteriores. Ocultar/excluir recebem apenas `{"id":"UUID_SALVO","versao":1}` no corpo JSON.

Respostas: 200 para leitura/salvamento/ocultação; 204 para exclusão; 400 para dados/imagem inválidos; 401 sem sessão; 404 para recurso privado/inexistente/de outra conta; 409 para versão desatualizada, perfil privado ao publicar ou limite de publicações; 413 para corpo excessivo; 415 para tipo de corpo inválido; 429 para processador de fotos ocupado. Erros seguem `{codigo,mensagem}`. Campos desconhecidos são rejeitados.

## Validação local

- 168 testes Java aprovados, incluindo 12 novos testes de publicação/API: propriedade, padrão privado, perfil obrigatório, campos públicos, sanitização de foto, snapshot estável, ocultação, exclusão em cascata, versões concorrentes, recriação e falha sem gravação parcial.
- 93 testes móveis aprovados, incluindo três novos para seleção explícita de campos e limites. TypeScript e lint sem erros.
- Integração HTTP/MySQL com duas contas temporárias: rascunho, envio de PNG convertido em JPEG, publicação, leitura por outra conta, perfil privado, ocultação, conflito e exclusão preservando o roteiro. Executar `node scripts/test-publications.mjs` com Docker/Node no PATH e ambiente local iniciado. Remove suas próprias fixtures.
- Exportação Android com 1600 módulos concluída; não equivale a APK nem teste físico.
- V028 aplicada localmente. Nenhuma hospedagem externa, nenhuma foto real publicada durante os testes.
- Totais existentes preservados: 32 contas, 12 roteiros e 22 rolês; tabelas de publicações e fotos vazias após a limpeza do teste.

## Celular — checklist adiado

É necessário **novo APK de desenvolvimento** com os dois módulos de imagem. Esta entrega não gerou nem instalou APK. O APK antigo pode abrir as telas e salvar relatos; ao tentar escolher uma foto sem módulo nativo, deve explicar que precisa da atualização.

- [ ] Instalar o novo APK como atualização, sem apagar os dados.
- [ ] Abrir um roteiro salvo → Compartilhar passeio; confirmar que começa privado.
- [ ] Digitar relato/legendas, conferir teclado, rolagem e navegação inferior.
- [ ] Escolher fotos retrato/paisagem, cancelar seletor e testar acesso negado/limitado à galeria.
- [ ] Conferir orientação, qualidade, proporções, legendas e remoção das três fotos; testar PNG/HEIC convertido pelo seletor.
- [ ] Testar pausa/retomada e encerramento do Android durante o seletor: nada deve ser publicado automaticamente; reabrir o rascunho salvo se o processo for encerrado.
- [ ] Salvar privado, sair e voltar; revisar antes de publicar; conferir que data, horários, participantes e orçamento não aparecem.
- [ ] Com perfil privado, publicar deve orientar a configurar o perfil; salvar rascunho antes de sair para essa configuração.
- [ ] Publicar e consultar em outra conta pelo perfil; conferir imagens e ausência de dados privados.
- [ ] Editar, ocultar publicação e ocultar perfil; na segunda conta, retomar/atualizar e conferir indisponibilidade.
- [ ] Excluir publicação e verificar roteiro intacto; excluir roteiro e verificar publicação removida.
- [ ] Perder conexão durante upload: manter texto, sem sucesso falso; recarregar para conferir se salvou antes de reenviar.
- [ ] Abrir em duas sessões e editar a mesma versão: conflito deve impedir sobrescrita.
- [ ] Regressão de login, mapa, roteiro offline, horários e notificações.

Nenhum item acima foi marcado como testado no aparelho nesta entrega.
