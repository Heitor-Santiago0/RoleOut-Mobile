# Recuperação de senha

Entrega local de 26/11/2026. O usuário confirmou o funcionamento do feed com fotos no APK anterior. Esta etapa adiciona recuperação por e-mail; ainda não foi publicada no Railway nem incluída em novo APK.

## Usar

Na tela de entrada, **Esqueci minha senha** abre o pedido de código. Após informar o e-mail, o usuário cola o código recebido, define e confirma a nova senha e volta para entrar. **Já tenho um código** permite retomar o processo sem pedir outro. Os campos de código e senha são limpos ao sair da tela e não são persistidos.

A resposta ao pedido é igual para e-mail cadastrado, não cadastrado ou temporariamente limitado: “Se houver uma conta com esse e-mail, enviaremos um código. Confira também o spam.” Isso não garante que a mensagem já tenha sido entregue. Se o envio não estiver configurado, a API retorna 503 para qualquer e-mail; a interface informa indisponibilidade.

## Banco, API e segurança

V031 cria `recuperacoes_senha`, uma linha por conta, com fila, janela de pedidos, tentativas, resumo SHA-256 do código e validade. Não altera contas ou roteiros existentes. O código aleatório tem 256 bits (43 caracteres URL-safe), é gerado no trabalhador de envio, permanece em memória durante o envio e não é retornado pela API nem gravado em texto puro no banco/logs.

O código expira em 15 minutos. Um novo pedido aceito invalida o anterior. Limites: intervalo de um minuto, até três pedidos por hora por conta, além do limitador já existente do Controller (10 operações de autenticação/minuto por endereço observado, por instância). A fila faz até três tentativas SMTP, com intervalo mínimo de dois minutos e prazo de uma hora desde o pedido. Cada tentativa emite um código novo: usar sempre o e-mail mais recente. Uma falha de rede pode impedir saber se a mensagem anterior foi entregue.

A fila sobrevive a reinício do servidor. O trabalhador consulta até dez contas a cada ciclo, com reserva temporária de dois minutos. Não mantém conexão de banco durante o envio SMTP. Falhas registram somente uma mensagem genérica; estado/tentativas podem ser auditados na tabela, sem expor o código. Pedidos que esgotaram tentativas aguardam uma nova solicitação, respeitando os limites.

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

## Ativação online pendente

Configurar um remetente autorizado e um servidor SMTP. Variáveis privadas no Railway: `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_TLS` (`starttls` por padrão ou `ssl`). A configuração exige credenciais, TLS e validação de identidade do servidor. Não colocar essas credenciais no app, Git ou ZIP. Não usar `MAIL_LOCAL_TEST` no servidor público; essa combinação é recusada.

Ordem: aplicar V031 preservando o banco; configurar o remetente/SMTP; publicar a API; testar entrega real e pasta de spam; gerar o APK com o botão novo. Não foi contratado serviço nem enviado e-mail a usuários nesta entrega. A disponibilidade de SMTP e suas portas no provedor de hospedagem precisa ser confirmada antes da ativação; a validação local não comprova entrega externa.

## Validação e pendências

- Suíte Java: 200 testes aprovados, incluindo código de uso único, expiração, limite por conta, fila/reinício, repetição após falha, concorrência, sessões antigas e TLS.
- Mobile: 98 testes aprovados; TypeScript e lint de `src/` aprovados.
- Exportação Android/Hermes concluída. Isso não é um novo APK instalado nem substitui o teste de teclado no aparelho.
- Teste HTTP com MySQL/Tomcat e entrega SMTP no Mailpit aprovou pedido genérico, mensagem recebida, senha inválida sem consumo do código, confirmação, revogação da sessão anterior, recusa da senha antiga, login com nova senha e rejeição de reutilização. Conta temporária removida ao final.
- Migração local manteve 32 contas, 12 roteiros e 25 rolês.
- No celular, após disponibilizar a próxima versão: pedir/colar código, verificar teclado e retorno à entrada, testar senha inválida, código expirado/reutilizado e recusa da sessão antiga. Confirmar que roteiros e perfil continuam preservados e reativar avisos após entrar novamente.
- Operação com múltiplas réplicas requer limitação de tráfego compartilhada na entrada; o limitador em memória atual é por instância. Ainda não há proteção distribuída contra abuso nem garantia de entrega na caixa principal.
