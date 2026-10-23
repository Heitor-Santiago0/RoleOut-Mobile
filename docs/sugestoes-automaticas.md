# Sugestões automáticas no Explorar

Entrega local de 20/10/2026: aviso dentro do aplicativo enquanto o usuário utiliza o Explorar. Não é push, não pede nova permissão de notificações, não faz localização em segundo plano e não substitui o canal de alterações dos roteiros salvos. A etapa seguinte é avaliar a detecção de viagens em segundo plano, com configuração nativa e homologação próprias.

## Fluxo completo

1. Minha conta → Sugestões automáticas no Explorar. Vêm desativadas.
2. Ativar, informar a cidade de referência, escolher se permite outras cidades e salvar. Selecionar frequência de no máximo uma sugestão a cada 24, 72 ou 168 horas. Os interesses continuam em Minhas preferências.
3. Abrir Explorar com a localização autorizada. Reutiliza a posição do mapa e a cidade obtida por geocodificação reversa. A busca manual por cidade não simula uma viagem nem altera essa posição.
4. A cada minuto enquanto a tela está em foco e o app ativo, o aplicativo pode conferir uma sugestão. Posições com mais de dois minutos, inválidas ou sem cidade são ignoradas. Erros não impedem a busca normal e permitem repetir a tentativa.
5. O servidor consulta o catálogo aprovado em até 5 km, cruza as características com os interesses atuais e exige programação compatível agora. Sem horário suficiente ou correspondência específica, não interrompe o usuário; as recomendações comuns continuam disponíveis.
6. O cartão permite conhecer o local, dispensar ou abrir os controles. Desativar remove a cidade de referência armazenada e impede novas sugestões. O limite temporal anterior é preservado para evitar repetições ao alternar a ativação.

## Critérios e limites

- Somente características publicadas (música, culinária ou ambiente); afinidade ampla de categoria não basta para um aviso automático.
- Prioriza número de dimensões correspondentes, depois distância e ID. Respeita datas, término exclusivo, pausas, exceções de fechamento e eventos que atravessam a meia-noite.
- Atividades sem data exigem um intervalo de funcionamento conhecido. Eventos com início e término completos podem usar seu intervalo quando não há regra de funcionamento para o dia. Regras conhecidas de fechamento prevalecem.
- Usa o relógio do servidor convertido ao fuso informado pelo aparelho. Fuso próprio por estabelecimento permanece pendente; viagens entre fusos exigem validar o ajuste do telefone.
- A cidade geocodificada precisa corresponder à cidade do cadastro, ignorando acentos, caixa e espaços externos. Nomes divergentes, cidades homônimas e limites municipais ainda precisam de identificação geográfica mais precisa. Não prova uma viagem física.
- Conta, frequência e autorização são conferidas no servidor a cada avaliação. Transação com bloqueio da conta impede dois avisos concorrentes. O horário é consumido quando o servidor retorna uma sugestão, não quando há prova de visualização. Se a resposta se perder, pode não haver cartão até o próximo intervalo; prioriza evitar repetições.
- Não armazena coordenadas, cidade detectada, histórico de viagens ou lista de locais sugeridos. Persiste somente configuração, cidade de referência escolhida e instante do último aviso. Coordenadas seguem em corpo POST, sem serem incluídas na URL desta API. Infraestrutura não deve registrar corpos de requisições com localização.
- As escolhas valem por conta e persistem entre sessões; sem sessão autenticada, não há avaliação. Desativar estes cartões não desativa avisos push de alterações e vice-versa.
- A entrega não envia notificações reais ao aparelho. Localização/servidor/rede continuam necessários; não sugere offline. Esta etapa não requer novo APK nativo.

## Banco e API

Aplicar V025__sugestoes_automaticas.sql antes do novo WAR em banco existente. Cria sugestoes_config, sem reescrever cadastros, preferências ou roteiros. Nunca recriar o volume de dados.

Endpoint autenticado `/api/v1/sugestoes-automaticas`:

| Método | Contrato |
| --- | --- |
| GET | `{ativo, viagens, intervaloHoras, cidadeBase}`; padrão desativado, 24 horas |
| PUT | Mesmo objeto; booleanos e intervalo obrigatórios, cidade obrigatória quando ativo; permite desativar sem cidade |
| POST | `{latitude, longitude, cidade, fuso}`; retorna `{sugestao: null}` ou `{sugestao: {roleId, nome, cidade, distanciaKm, motivos, outraCidade}}` |

Não aceita ID de conta do cliente. Latitude/longitude finitas em faixa válida; fuso válido IANA/ZoneId; cidade até 100 caracteres. A escolha é feita pelo servidor, sem confiar em IDs de candidatos ou justificativas enviados pelo celular.

## Validação local concluída

- Backend: `mvn -B -o package`, 141 testes aprovados. Inclui autenticação, conta obtida da sessão, validação, horários e exceções, interesses, privacidade entre contas, frequência persistente, duas consultas concorrentes e consulta real ao catálogo H2 com exclusão de cancelados, pendentes e distantes.
- Aplicativo: 76 testes aprovados; novos casos conferem validade/frescor da localização e opções de frequência. TypeScript e lint passaram sem erros ou avisos. OpenAPI validado como YAML.
- V025 aplicada no MySQL local; WAR atualizado. Nenhuma conta ativada automaticamente. Preservados 22 rolês, 6 rascunhos e 11 roteiros.
- API local: nova rota retorna 401 sem autenticação; consulta existente de proximidade continua retornando seis pontos. O fluxo autenticado foi verificado em testes automatizados com banco H2, não por interação no telefone.
- Nenhuma notificação real enviada nem viagem física simulada no aparelho.

## Homologação no celular — adiada

- [ ] Conferir padrão desativado, salvar e reabrir as três frequências e a cidade; validar mensagens de erro de rede.
- [ ] Em um local de teste publicado e próximo, cadastrar característica que corresponda ao perfil e funcionamento no horário atual. Ativar sugestões e verificar cartão com motivo e distância.
- [ ] Abrir detalhe, voltar, dispensar, trocar de aba e reabrir app: não repetir dentro do intervalo.
- [ ] Conferir com segunda sessão da mesma conta: limite compartilhado. Trocar de conta não mostra sugestão antiga.
- [ ] Usar cidade de referência diferente da cidade atual: sem permitir outras cidades não aparece; permitindo, aparece com o nome da cidade detectada, respeitando frequência.
- [ ] Digitar outra cidade no catálogo: não tratar a busca manual como viagem.
- [ ] Negar/revogar localização, desligar GPS, perder rede e retornar; não usar posição antiga nem interromper a busca manual.
- [ ] Fechar/trocar de app: nenhuma avaliação em segundo plano; avisos de alterações existentes continuam independentes.
- [ ] Validar horário de pausa, fechamento, cancelamento, preferência alterada e aprovação pendente.
- [ ] Conferir teclado no campo de cidade, rolagem, fonte ampliada, botões e leitura do cartão no Samsung.

Não alterar o relógio do celular para contornar o intervalo: o controle usa o relógio do servidor. Para repetir cenários, utilizar contas de teste independentes ou testes automatizados com relógio controlado. O catálogo atual pode não gerar aviso até receber características e horários aprovados; não preencher informações fictícias em cadastros reais.

## Evolução: acompanhamento opcional em segundo plano

As restrições acima sobre funcionamento somente em primeiro plano descrevem a entrega inicial. Agora existe implementação Android separada e opcional em Perfil → Avisos em viagens, ainda dependente de novo APK e testes físicos. Sem essa ativação, as sugestões continuam apenas no Explorar. Consulta a mesma API e consome o mesmo limite. Ver [avisos em viagens](avisos-viagens.md).

## Política diária e configurações — atualização de 20/10/2026

A política padrão passou a ser `intervaloHoras: 0`: até três sugestões nas últimas 24 horas; depois disso somente candidatos com correspondência nos três grupos música, culinária e ambiente. Vários gêneros musicais contam como um único grupo. Existe intervalo mínimo de uma hora e o mesmo rolê não é repetido em 24 horas. A janela usa o relógio do servidor e não reinicia por troca de cidade/fuso, sessão ou ativação. As alternativas de 24/72/168 horas continuam disponíveis.

Novas configurações vêm preparadas com ativo/viagens true e política diária. Sem cidade de referência e autorizações do aparelho, não executam. Escolhas já salvas, inclusive desativação e frequência, são preservadas. O usuário pode selecionar a nova política em Configurações. Ativar todos os recursos não concede permissões do Android: o fluxo solicita cada autorização e informa resultados parciais, sem bloquear o uso se houver recusa.

V026 cria `sugestoes_entregas` (conta, ID do rolê, instante da última entrega) e muda somente o DEFAULT SQL do intervalo. A tabela suporta a janela de 24 horas e não registra posições/cidades; registros expirados são removidos na próxima entrega da conta. Não há histórico de deslocamentos. As decisões ficam serializadas pelo bloqueio da conta, inclusive entre sessões e cartões/viagens. Uma resposta elegível conta como entrega para o limite, independentemente de exibição efetiva pelo sistema.

Aplicada no MySQL local sem executar alterações em rolês ou roteiros. Conferência posterior: 22 rolês e 11 roteiros. API atualizada. Testes Java incluem três sugestões, exigência dos três grupos para extra, bloqueio de repetição e concorrência entre dispositivos. Testes físicos da nova política e do fluxo centralizado permanecem pendentes.
