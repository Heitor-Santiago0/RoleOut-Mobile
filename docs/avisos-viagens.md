# Avisos em viagens — Android

Entrega local de 20/10/2026. Implementação preparada; homologação nativa pendente em novo APK.

## Fluxo

1. Em Perfil → Configurações, salvar Sugestões automáticas ativadas, cidade de referência, frequência e permissão para outras cidades.
2. Abrir Avisos em viagens. Ler a explicação sobre localização em segundo plano, internet, bateria e aviso permanente do Android.
3. Tocar em Autorizar avisos em viagens. Autorizar notificações, localização e Permitir o tempo todo no Android. Nenhuma permissão é solicitada automaticamente.
4. O serviço Android recebe posições em segundo plano. Consultas limitadas no aparelho a uma a cada 15 minutos; a entrega efetiva também respeita o limite de diário (3 + extras com 3 grupos) ou 24/72/168 horas compartilhado com cartões e outros aparelhos.
5. Em outra cidade, havendo evento aprovado, próximo, compatível agora e com interesses correspondentes, uma notificação local abre o detalhe atual do rolê. Não é um novo envio FCM: a tarefa consulta a API e solicita a apresentação ao Android.
6. Desativar no perfil, sair/trocar de conta ou perder a autorização interrompe o serviço. Depois de nova sessão é necessário autorizar novamente. Não reinicia silenciosamente.

## Implementação e dados

- `expo-task-manager` compatível com SDK 57 e tarefa registrada no ponto de entrada `index.ts`, antes de montar telas.
- `expo-location` configurado para background e foreground service Android. iOS não habilitado.
- Consentimento vinculado à sessão em SecureStore; guarda também somente instante da última tentativa. Não persiste coordenadas, endereços nem histórico de cidades.
- Geocodificação reversa somente durante o acompanhamento autorizado, após verificar configurações da conta, limitada a uma tentativa por 15 minutos. Se não resolver a cidade, não sugere.
- Reutiliza GET/POST `/sugestoes-automaticas`, a tabela V025 e a seleção/frequência atômica existentes. V026 adiciona controle de entregas (ID do rolê e instante) para quota diária e não repetição. Não altera roteiros nem escolhas anteriores.
- Rejeita posições com mais de dois minutos, futuras ou precisão pior que 500 metros. Sem rede não usa recomendações antigas. Sessão e permissões são verificadas novamente após a consulta.
- Uma resposta elegível consome o limite no servidor mesmo se o Android não exibir a notificação; evita repetição, mas não comprova entrega. Não há garantia de disponibilidade/vaga.
- Desativação em outro aparelho é observada na próxima consulta bem-sucedida; desligar neste aparelho remove o consentimento local imediatamente.

## Limites

Precisa de novo APK com os módulos/permissões nativos. APK antigo continua utilizável e informa a necessidade de atualização ao tentar ativar. O desenvolvimento continua local; a API precisa estar alcançável por USB ou rede. Uma viagem fora dessa conexão não terá consulta disponível até existir hospedagem acessível.

O Android controla a execução. Encerrar o app, forçar parada, reiniciar o aparelho ou restrições de bateria podem interromper o serviço. A tela de permissões pode exigir retornar ao app. A cidade depende do geocodificador do aparelho e o horário usa o fuso configurado no dispositivo. Não é navegação contínua nem acompanhamento garantido com app encerrado.

## Verificação

143 testes Java e 83 testes móveis passaram, incluindo consentimento, autorização da conta, cidade base com acentos, localização inválida/antiga, ausência de candidatos, rede, saída da conta durante consulta e payload seguro de navegação. TypeScript e lint aprovados.

## Testes no novo APK — pendentes

- [ ] Ativar somente por toque; negar cada permissão e conferir recuperação sem bloquear Explorar.
- [ ] Confirmar aviso permanente do Android e desligamento pelo Perfil.
- [ ] Em conta de teste, definir outra cidade base e catálogo aprovado com funcionamento atual e interesse específico. Colocar em segundo plano e receber uma sugestão; tocar e abrir detalhes.
- [ ] Verificar ausência de repetição e compartilhamento da frequência com cartões no Explorar.
- [ ] Revogar permissão, sair da conta e trocar de conta durante uma consulta; nenhum aviso posterior da sessão anterior.
- [ ] Testar sem rede, localização imprecisa, GPS desligado, retorno ao app, encerramento e reinício do celular.
- [ ] Validar uso real de bateria e geocodificação no Samsung. O teste com cidade base diferente não comprova detecção numa viagem física.
- [ ] Confirmar que avisos de alterações dos roteiros e navegação existentes permanecem funcionando.

## Confiabilidade da ativação — 22/10/2026

A sequência centralizada de permissões agora mostra resultados individuais e só confirma recursos cuja ativação foi comprovada. GPS e geocodificação têm limite de 20 segundos por operação; a demora não bloqueia a configuração dos demais avisos. Respostas tardias não ativam sugestões. Sair da tela ou trocar de sessão interrompe os próximos passos. O serviço de viagens confirma que a tarefa nativa foi iniciada antes de anunciar sucesso.

Validação: 88 testes móveis aprovados, TypeScript e lint sem erros. Cinco novos testes cobrem sucesso parcial, recusa, cancelamento durante uma operação, ausência de solicitações com fluxo inativo, GPS lento e erros imediatos. Nenhuma alteração de banco/API nesta revisão. Nenhum APK novo gerado nesta entrega.

Testes físicos adicionais pendentes:
- [ ] Recusar localização e confirmar que os demais controles continuam disponíveis.
- [ ] Desligar o GPS e verificar que a sequência não permanece bloqueada.
- [ ] Sair da tela durante uma etapa e confirmar que não aparecem novas solicitações.
- [ ] Retornar das configurações Android e conferir o estado real das permissões.
- [ ] No novo APK, ativar viagens e confirmar o serviço/aviso permanente antes do teste em segundo plano.

## APK de homologação — 22/10/2026

Build Android de desenvolvimento solicitado para validar avisos de viagem e configuração de permissões:
https://expo.dev/accounts/toledosantiago0/projects/roleout-mobile/builds/df2f4e52-a61a-404a-ab5e-38398d253ba5

Usa o certificado local existente, pacote br.com.roleout.mobile e API local. Instalar como atualização, sem desinstalar a versão anterior. O desenvolvimento ainda precisa do Metro e da API alcançáveis. A instalação não concede permissões nem confirma funcionamento em segundo plano. Acompanhar o estado da compilação no link antes de instalar.

Build df2f4e52-a61a-404a-ab5e-38398d253ba5 concluído com sucesso (FINISHED) em 22/10/2026. APK baixado em mobile/.expo/apks/roleout-travel-2026-10-22.apk (ignorado pelo Git). Inspeção do AndroidManifest.xml confirmou br.com.roleout.mobile, ACCESS_BACKGROUND_LOCATION, FOREGROUND_SERVICE_LOCATION e POST_NOTIFICATIONS. Isso verifica a inclusão das permissões no binário, não a autorização do usuário nem a entrega real em segundo plano. Instalação e testes físicos permanecem pendentes.

Download: https://expo.dev/artifacts/eas/eK0c6cNByXOAHbODzwIua-MhJ4-n1T7HfESPu6LnT2M.apk

### Ajuste de confirmação das permissões — 22/10/2026
- A ação de ativar recursos é substituída por “Recursos ativados” quando localização, notificações, sugestões, registro de avisos e serviço de viagens estão confirmados.
- A tela consulta novamente a situação ao abrir, voltar do Android e salvar/desativar recursos nas seções individuais. Não solicita permissões automaticamente nessa conferência.
- Ativação parcial mantém a possibilidade de concluir; erro na consulta não é tratado como sucesso.
- Validação: TypeScript e lint sem erros; cinco testes da sequência de ativação aprovados.
- Pendente no Samsung: concluir ativação, sair e voltar às Configurações, confirmar ausência do botão azul; desativar um recurso e verificar atualização do resumo.
