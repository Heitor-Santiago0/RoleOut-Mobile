# Avisos com o aplicativo fechado

Implementação Android para alterações dos rolês salvos. Em 16/10/2026, os arquivos do Firebase foram conferidos para roleout-mobile e br.com.roleout.mobile; a credencial FCM v1 foi vinculada no EAS e GOOGLE_SERVICES_JSON foi cadastrada como variável de arquivo secreta no ambiente development. O servidor local está com PUSH_ENABLED=true. Build instalado: https://expo.dev/accounts/toledosantiago0/projects/roleout-mobile/builds/4a32473b-5c7d-4f11-aa11-e7ab98cdc793. **Em 18/10/2026, o registro e uma notificação real de teste foram validados no Samsung com o app em segundo plano.** A chave privada permanece fora do repositório e não é incorporada ao aplicativo.

O diagnóstico no Android identificou `API_KEY_SERVICE_BLOCKED`: o arquivo cliente deste APK usa a chave também utilizada pelo mapa, que permitia apenas Maps SDK for Android. Com autorização do responsável, foram adicionadas Firebase Installations API e FCM Registration API, mantendo a restrição de pacote e certificado Android. O registro passou a funcionar sem novo APK. Para um próximo build, revisar a separação entre as chaves de Maps e Firebase conforme a recomendação do Google.

O teste manual enviou uma mensagem identificada como teste diretamente ao Expo, usando o dispositivo registrado, sem modificar eventos ou roteiros. O ticket e o recibo retornaram `ok`, e o usuário confirmou a exibição no celular. Isso valida o caminho Expo → FCM → Android em segundo plano; não substitui os testes abaixo de alteração aprovada, fila automática, toque e revogação. Encerramento do processo e parada forçada não foram validados nessa execução.

## Fluxo

- Perfil → Avisos de alterações → Ativar avisos neste celular. A permissão do Android é solicitada somente por ação do usuário. É possível desativar os avisos ou abrir as permissões do sistema.
- A API registra o Expo Push Token na sessão autenticada. Não aceita um ID de conta enviado pelo cliente. Trocar de conta ou de token invalida o registro anterior e sua fila. Sair da conta remove o registro por cascata. Sessões expiradas não enviam: **atualmente o login dura 24 horas**, portanto é preciso entrar e ativar novamente depois da expiração. Não há renovação automática da sessão nesta entrega.
- Um trabalhador no Tomcat confere alterações a cada 60 segundos, com o app aberto ou fechado. Somente registros publicados são observados, sem ler rascunhos em análise. Mudanças entre duas conferências são consolidadas no estado observado; não é um histórico de cada edição intermediária.
- São considerados roteiros de hoje ou futuros, com paradas pendentes. Compara datas, início, término, funcionamento, cancelamento, remoção, cidade e disponibilidade do cadastro. Horários usam a convenção atual do app e o dia do servidor em America/Sao_Paulo; fusos por evento continuam pendentes.
- Ao ativar, a referência é o cadastro atual: não envia uma enxurrada de mudanças antigas. Os avisos internos continuam comparando o planejamento original. A criação de novos roteiros é detectada na conferência seguinte; incompatibilidades existentes nessa primeira conferência podem gerar um aviso.
- Estado e fila persistem no banco. Consultas repetidas e reinício do trabalhador não geram novos avisos da mesma mudança. A referência do planejamento, preços e progresso não são modificados.
- O aviso na tela bloqueada é genérico, sem nome de local, data, participantes ou coordenadas. Ao tocar, o app consulta a API e verifica a propriedade do roteiro antes de abrir seus detalhes. Sem rede ou com outra conta, não exibe o roteiro alheio.

## Banco e API

Aplicar **V021__notificacoes_push.sql** uma vez, após V020, antes de implantar o WAR. Cria `push_dispositivos`, `push_estado` e `push_envios`; não reescreve dados existentes. As chaves estrangeiras removem fila/estado ao excluir a assinatura, sessão ou roteiro.

Aplicar também **V022__paradas_dos_avisos.sql** antes do WAR atualizado. Adiciona `roles_afetados` aos envios, sem apagar registros. Cada novo aviso guarda os IDs das paradas que motivaram a mudança. Antes do envio ou de uma nova tentativa, se nenhuma delas continuar pendente no roteiro, o aviso é cancelado, mesmo que outras paradas permaneçam. Se ao menos uma ainda for relevante, o aviso segue. Registros antigos com esse campo nulo mantêm o comportamento anterior, pois sua origem não pode ser reconstruída com segurança. Avisos já enviados não podem ser recolhidos; a consulta de recibo não reenvia a mensagem.

Endpoint autenticado `/api/v1/notificacoes`:

| Método | Entrada / resultado |
| --- | --- |
| GET | `{ativo, envioConfigurado, ultimoErro}` da sessão atual |
| PUT | `{expoToken}` registra o dispositivo, de forma idempotente |
| DELETE | Revoga os avisos da sessão e remove a fila pendente |

O servidor usa os endpoints HTTPS fixos do Expo Push Service. Envia notificações visíveis com canal `horarios` e validade de uma hora. Consulta recibos após 15 minutos; `ACEITO_PROVEDOR` significa aceitação por FCM/APNs, **não prova de visualização ou entrega ao aparelho**. Falhas temporárias têm espera progressiva, até seis tentativas por etapa. Erros permanentes ficam como FALHOU; `DeviceNotRegistered` remove a assinatura. Registros de envio são descartados após 30 dias. Tokens não são escritos nos logs.

Os bloqueios de linhas evitam envio concorrente do mesmo trabalho; transações agrupam a mudança observada e a inclusão na fila. Como todo envio externo, uma falha depois de o Expo aceitar e antes de salvar o ticket pode causar repetição. Avisos já aceitos pelo provedor não podem ser recolhidos ao desativar. A checagem usa somente paradas ainda pendentes.

## Configuração necessária

1. Criar um projeto Firebase (pode associar ao projeto Google Cloud existente `roleout-mobile`) e cadastrar o app Android com pacote **br.com.roleout.mobile**. Baixar o arquivo cliente `google-services.json`.
2. Informar o caminho desse arquivo em `GOOGLE_SERVICES_JSON` no ambiente do build, preferencialmente variável de arquivo do EAS. `app.config.js` o associa a `android.googleServicesFile`. O arquivo não deve ser confundido com uma chave privada de conta de serviço.
3. Seguir a configuração oficial do FCM v1 e cadastrar a credencial de serviço no EAS para o projeto Expo **94098a22-1063-4f2e-9dda-5515a59324d4**, pacote acima. Guardar a chave privada fora do repositório e do aplicativo; não colar no chat nem em variáveis `EXPO_PUBLIC_*`.
4. Gerar e instalar novo APK development. O plugin `expo-notifications` está configurado; este módulo exige rebuild nativo. O servidor local/Metro continua necessário para abrir esta versão de desenvolvimento. Para abrir independentemente do notebook, ainda será necessária a entrega de APK independente e API hospedada.
5. Após configurar o Firebase e o APK, iniciar/recriar somente o serviço Tomcat com `PUSH_ENABLED=true` no ambiente do Compose. O padrão é `false`; migrations não são executadas pelo WAR. `EXPO_ACCESS_TOKEN` é opcional, necessário se ativar a segurança adicional de push no EAS. Nunca usar uma variável pública do app para esse segredo.
6. Ativar os avisos no perfil, aceitar a permissão e confirmar o registro no servidor. O celular precisa de internet, mas o recebimento de push não exige estar na mesma rede do notebook. O trabalhador precisa de acesso ao banco e à internet.

Referências: [SDK 57 Notifications](https://docs.expo.dev/versions/v57.0.0/sdk/notifications/), [configuração push](https://docs.expo.dev/push-notifications/push-notifications-setup/), [FCM v1](https://docs.expo.dev/push-notifications/fcm-credentials/), [envio e recibos](https://docs.expo.dev/push-notifications/sending-notifications/).

## Validação

Em 20/10/2026, o teste integrado de aprovação foi ampliado para executar envio e recibo com provedor simulado, incluindo reinício do trabalhador, ausência de envio antes da aprovação, deduplicação, preservação do roteiro e bloqueio de consulta por outra conta. No aplicativo, falhas de registro agora distinguem configuração Firebase, conexão e etapa Android/Expo, sem exibir mensagens brutas que possam conter tokens. O botão de repetir tentativa executa novamente a ação de ativar/desativar; quando a consulta inicial falha, a ação disponível apenas atualiza a situação dos avisos.

Automatizada com banco H2 e provedor simulado: autorização, isolamento de contas, opt-out, expiração, troca de conta, pendentes/concluídos, exclusão, datas passadas, deduplicação, retorno ao horário anterior, reinício, espera em falha de rede, recibo inválido e credencial inválida. Teste integrado de aprovação comprova que a edição pendente não envia e que a aprovação enfileira uma única vez. Parsing de tickets/recibos e validação da ação de navegação têm testes próprios. Nenhuma notificação real foi enviada nesses testes.

No Samsung, depois da configuração:

**Pendências anotadas em 20/10/2026:** testes físicos adiados a pedido do usuário, pois o celular está sem bateria. Não executar envios reais nesta etapa. Ao retomar, entrar novamente e ativar os avisos se a sessão de 24 horas tiver expirado; conferir os endereços da API e do Metro, pois a rede do notebook mudou desde o teste anterior.

1. Salvar um roteiro futuro, ativar os avisos, fechar o app normalmente e bloquear a tela.
2. Em outra conta autorizada, editar um horário e confirmar que nada chega antes da aprovação.
3. Aprovar a alteração, aguardar a conferência e verificar aviso, toque, conta correta e manutenção do horário original.
4. Repetir com cancelamento, com avisos desativados, sessão encerrada e parada concluída.
5. Validar permissão negada, ausência de rede e retomada; conferir recibos no servidor. Não confundir recibo aceito com entrega visual.
6. Em roteiro com duas paradas, deixar um aviso pendente após falha de envio e concluir ou remover somente a parada alterada. Confirmar que a nova tentativa não envia um aviso sem motivo. Repetir mantendo outra parada alterada pendente: nesse caso, o aviso deve continuar.
7. Testar as mensagens de erro de registro e o botão “Repetir tentativa”; tocar no aviso recebido e confirmar que abre o roteiro da conta correta.

A cobertura automatizada de V022 inclui conclusão da parada afetada, remoção durante espera para nova tentativa, outras paradas afetadas ainda pendentes, conclusão de uma parada não afetada, recibo após conclusão, registros legados e reinício do trabalhador. Nenhuma dessas verificações exige o telefone.

Forçar a parada do aplicativo nas configurações do Android pode impedir avisos até reabrir. Restrições de bateria/rede e indisponibilidade de Expo/FCM também podem atrasar ou impedir a entrega. Sem conexão, roteiros offline continuam disponíveis, mas não recebem informações novas.

Sugestões proativas por interesses e viagens não estão incluídas neste canal de alterações. Há uma entrega separada de [cartões automáticos no Explorar](sugestoes-automaticas.md), enquanto o aplicativo está em uso. A autorização não inicia rastreamento de localização em segundo plano.
