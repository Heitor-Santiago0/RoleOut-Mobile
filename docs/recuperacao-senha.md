# Recuperação de senha

Atualizada em 26/11/2026: API publicada no Railway com Brevo via HTTPS e V031 aplicada online. Remetente verificado e e-mail de configuração entregue conforme evento do Brevo. APK gerado e conferido em 26/11/2026; instalação e validação física pendentes.

## Usar

Na tela de entrada, **Esqueci minha senha** abre o pedido de código. Após informar o e-mail, o usuário cola o código recebido, define e confirma a nova senha e volta para entrar. **Já tenho um código** permite retomar o processo sem pedir outro. Os campos de código e senha são limpos ao sair da tela e não são persistidos.

A resposta ao pedido é igual para e-mail cadastrado, não cadastrado ou temporariamente limitado: “Se houver uma conta com esse e-mail, enviaremos um código. Confira também o spam.” Isso não garante que a mensagem já tenha sido entregue. Se o envio não estiver configurado, a API retorna 503 para qualquer e-mail; a interface informa indisponibilidade.

## Banco, API e segurança

V031 cria `recuperacoes_senha`, uma linha por conta, com fila, janela de pedidos, tentativas, resumo SHA-256 do código e validade. Não altera contas ou roteiros existentes. O código aleatório tem 256 bits (43 caracteres URL-safe), é gerado no trabalhador de envio, permanece em memória durante o envio e não é retornado pela API nem gravado em texto puro no banco/logs.

O código expira em 15 minutos. Um novo pedido aceito invalida o anterior. Limites: intervalo de um minuto, até três pedidos por hora por conta, além do limitador já existente do Controller (10 operações de autenticação/minuto por endereço observado, por instância). A fila faz até três tentativas de envio, com intervalo mínimo de dois minutos e prazo de uma hora desde o pedido. Cada tentativa emite um código novo: usar sempre o e-mail mais recente. Uma falha de rede pode impedir saber se a mensagem anterior foi entregue.

A fila sobrevive a reinício do servidor. O trabalhador consulta até dez contas a cada ciclo, com reserva temporária de dois minutos. Não mantém conexão de banco durante o envio ao provedor. Falhas registram somente uma mensagem genérica; estado/tentativas podem ser auditados na tabela, sem expor o código. Pedidos que esgotaram tentativas aguardam uma nova solicitação, respeitando os limites.

A confirmação revalida validade e propriedade sob bloqueio da conta, grava o hash da nova senha e revoga todas as sessões na mesma transação. As inscrições push vinculadas às sessões também são removidas por cascata. Depois do novo login, será necessário ativar avisos nessa sessão novamente. O código não pode ser reutilizado, inclusive em duas confirmações concorrentes. Login também revalida o hash após adquirir o bloqueio, evitando criar sessão com uma senha substituída durante sua verificação.

Arquivos: `api/AuthServlet.java`, `service/PasswordRecoveryService.java`, `service/SmtpRecoveryMail.java`, `dao/PasswordRecoveryDAO.java`, `model/PasswordRecoveryData.java`, `config/DatabaseLifecycleListener.java`, `mobile/src/app/recover-password.tsx`. Mantidas as camadas Controller → Service → DAO. Envio desacoplado pela interface `RecoveryMail`.

## Contratos

Base `/api/v1`, JSON, sem exigir login. Respostas `Cache-Control: no-store`. [OpenAPI](openapi-recuperacao.json).

| Método | Corpo | Resultado |
| --- | --- | --- |
| POST `/auth/recuperacao` | `{"email":"pessoa@example.com"}` | 202 com `mensagem` genérica; 503 se não configurado |
| POST `/auth/nova-senha` | `{"codigo":"codigo_recebido","senha":"nova_senha"}` | 204 sem sessão automática; 400 para código inválido/expirado ou senha inválida |

Senha continua aceitando de 6 a 128 caracteres, conforme pedido anterior. Campos extras são recusados. Código inválido/expirado usa `CODIGO_INVALIDO`; formato inválido, `PARAMETRO_INVALIDO`; excesso de tentativas no Controller, 429. Nenhum endpoint aceita escolher `contaId` para a redefinição.

## Teste local sem enviar e-mail externo

Aplicar V031 uma vez em banco existente. Em banco vazio, o Compose aplica as migrações automaticamente. Compilar com `mvn -B clean verify` e iniciar:

```sh
docker compose -f compose.integration.yml -f compose.mail-test.yml up -d
```

A caixa local fica em http://127.0.0.1:8025. Mailpit recebe as mensagens dentro da rede Docker e não as entrega pela internet. O Compose adicional define `MAIL_LOCAL_TEST=true` e remetente fictício. Não usar esse arquivo em produção. Para voltar ao ambiente sem envio, recriar o Tomcat somente com `compose.integration.yml` e parar o serviço mailpit; isso não exige apagar o volume MySQL.

## Configuração online — Brevo HTTPS

Railway Free/Trial/Hobby bloqueia SMTP de saída. Produção usa `MAIL_PROVIDER=brevo`, `BREVO_API_KEY`, `MAIL_FROM` (remetente verificado) e `MAIL_FROM_NAME`. São variáveis privadas do serviço roleout-api. O arquivo local ignorado `.env.brevo` serve somente para configuração assistida: Java não carrega arquivos dotenv automaticamente. O exemplo versionado não contém valores reais.

`RecoveryMailProvider` seleciona o adaptador; padrão SMTP preserva o Mailpit local e ambientes SMTP existentes. Provedor desconhecido desativa a recuperação. `BrevoRecoveryMail` envia JSON ao endpoint HTTPS fixo do Brevo, com prazo de conexão de 10 segundos e requisição de 15 segundos, sem seguir redirecionamentos. Apenas HTTP 201 conta como aceite; respostas e causas do provedor não são registradas para não expor destinatários/códigos/chaves. A fila persistente mantém os limites e novas tentativas existentes.

O plano gratuito do Brevo permite até 300 e-mails/dia. A entrega depende das políticas, limites e disponibilidade do provedor; aceite não garante caixa principal. O remetente de provedor gratuito pode ter seu endereço adaptado pelo Brevo. Não foi ativado plano pago. Isso não altera os limites/créditos de hospedagem do Railway.

Referências: [Railway](https://docs.railway.com/networking/outbound-networking), [API Brevo](https://developers.brevo.com/reference/send-transac-email).

V031 foi aplicada de forma aditiva, preservando 33 contas, 12 roteiros e 25 rolês online. O serviço temporário foi removido. Deploy da API: `723f9e70-9d9b-48e3-8e36-cfda9d426dcc` (SUCCESS). Upload limitado ao código de servidor, sem arquivo local de credenciais. O serviço Entec não foi alterado.

Teste externo: um e-mail de configuração enviado diretamente ao endereço do remetente autorizado foi aceito (201) e teve evento `delivered`. Isso valida chave/remetente/provedor, mas não é uma redefinição real de senha. No servidor publicado: catálogo 200, recuperação para endereço fictício 202/no-store, código inexistente 400/CODIGO_INVALIDO e campos extras rejeitados. Nenhuma senha real foi alterada. Fluxo completo de fila, entrega, redefinição e revogação foi testado localmente; permanece a validação ponta a ponta online no celular após instalar o APK abaixo.

## Validação e pendências

- Suíte Java: 203 testes aprovados, incluindo código de uso único, expiração, limite por conta, fila/reinício, repetição após falha, concorrência, sessões antigas, TLS e contrato HTTPS do Brevo (erros, redirecionamentos e ausência de dados sensíveis nas exceções).
- Mobile: 98 testes aprovados; TypeScript e lint de `src/` aprovados.
- Exportação Android/Hermes concluída. Isso não é um novo APK instalado nem substitui o teste de teclado no aparelho.
- Teste HTTP com MySQL/Tomcat e entrega SMTP no Mailpit aprovou pedido genérico, mensagem recebida, senha inválida sem consumo do código, confirmação, revogação da sessão anterior, recusa da senha antiga, login com nova senha e rejeição de reutilização. Conta temporária removida ao final.
- Migração local manteve 32 contas, 12 roteiros e 25 rolês.
- No celular, após disponibilizar a próxima versão: pedir/colar código, verificar teclado e retorno à entrada, testar senha inválida, código expirado/reutilizado e recusa da sessão antiga. Confirmar que roteiros e perfil continuam preservados e reativar avisos após entrar novamente.
- Operação com múltiplas réplicas requer limitação de tráfego compartilhada na entrada; o limitador em memória atual é por instância. Ainda não há proteção distribuída contra abuso nem garantia de entrega na caixa principal.

## APK de recuperação — 26/11/2026

Build `c265ade7-ad54-4c40-a2f2-c4b0f4803a0d`, commit `79f2bae`, perfil preview standalone, status FINISHED. [Instalar APK](https://expo.dev/artifacts/eas/PXPm1A9Q1Bv33_f6085TN8Dm6UHmmSUicy4p-UESKwU.apk). Mesmo pacote e credencial Android usados anteriormente.

Conferidos: integridade ZIP, pacote br.com.roleout.mobile, endereço HTTPS de produção no bundle, rotas /recover-password, /auth/recuperacao e /auth/nova-senha, ausência da URL local da API. TypeScript, lint src e os dois testes focados de recuperação passaram antes da compilação. SHA-256: `fd79e5294cb2bfacf9c91a056b0329b04e8ecaabcb5dc5bc5d419caf52ec0453`. Tamanho: 136843981 bytes.

Ainda validar no aparelho: instalar sobre a versão anterior, solicitar código com e-mail cadastrado, conferir caixa/spam, definir nova senha e entrar; confirmar senha antiga recusada e perfil/roteiros preservados. Não registrar código ou senha em capturas/logs.
