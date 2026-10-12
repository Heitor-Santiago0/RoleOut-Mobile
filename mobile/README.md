# RolêOut · Aplicativo

[← Projeto](../README.md) · [Funcionalidades](../docs/funcionalidades.md) · [Etapas](../docs/etapas.md)

Aplicativo React Native, Expo SDK 57 e TypeScript, com navegação por Expo Router. A implementação atual está na branch `develop/api-pool`. Android é a plataforma em validação; a prévia web auxilia a revisão visual e não substitui o teste nativo.

## Preparar o ambiente

Use Node.js 24 e pnpm. Com a API iniciada conforme o README principal, execute na pasta `mobile`:

```bash
pnpm install --frozen-lockfile
```

Crie ou edite `.env.local`, sem sobrescrever configurações existentes:

```dotenv
EXPO_PUBLIC_API_URL=http://127.0.0.1:8082/RoleOut/api/v1
# Necessária para gerar a versão Android própria com Google Maps:
GOOGLE_MAPS_ANDROID_API_KEY=SUA_CHAVE_RESTRITA
```

Substitua o marcador pela chave real somente no arquivo local. Não envie arquivos de ambiente ou certificados ao Git. O endereço da API é público no aplicativo e não deve conter senhas ou tokens.

| Onde executar | Endereço da API |
| :--- | :--- |
| Navegador do notebook | `http://127.0.0.1:8082/RoleOut/api/v1` |
| Emulador Android padrão | `http://10.0.2.2:8082/RoleOut/api/v1` |
| Celular físico | IP de rede do notebook e uma porta publicada nessa interface |
| Ambiente remoto | URL HTTPS do servidor hospedado |

O Compose de integração publica apenas `127.0.0.1:8082`. Para acesso físico pelo Wi-Fi, configure uma publicação de porta na interface de rede do notebook e a regra de firewall correspondente. Não use `localhost` no celular para acessar o notebook. Mudanças de rede podem alterar o IP.

## Prévia no navegador

```bash
pnpm exec expo start --web --localhost --port 8081
```

Abra `http://localhost:8081`. As origens dessa prévia estão configuradas no Compose de integração. O token web fica em memória; atualizar a página encerra essa persistência local. O mapa nativo não é exibido na prévia.

## Android: desenvolvimento

O perfil `development` em `eas.json` gera APK com Expo Dev Client. Ele permite usar a configuração nativa própria do Google Maps.

1. Instale o APK gerado no aparelho Android.
2. Ajuste a URL da API para um endereço alcançável pelo aparelho.
3. Inicie o servidor de desenvolvimento na pasta `mobile`:

```bash
pnpm exec expo start --dev-client --lan
```

4. Abra o RolêOut instalado e conecte-o ao servidor indicado pelo Expo.

No teste por LAN, mantenha celular e notebook na mesma rede e confira o acesso à API. O APK de desenvolvimento depende desse servidor para carregar o código; ele não é a versão independente prevista para convidados à distância.

### Google Maps e assinatura

O pacote Android configurado é `br.com.roleout.mobile`. A chave precisa estar restrita a **Maps SDK for Android** e à combinação do pacote com o SHA-1 do certificado que assina o APK. Não use o certificado de outro aplicativo.

`app.config.js` lê `GOOGLE_MAPS_ANDROID_API_KEY` e configura o plugin de `react-native-maps`. Para compilar na nuvem, a variável também precisa existir no ambiente `development` do EAS. Alterações na chave nativa ou na configuração de assinatura exigem novo APK; reiniciar o servidor JavaScript não altera o binário instalado.

O perfil usa credenciais locais: `credentials.json` aponta para o certificado de testes mantido fora do Git. Esses arquivos precisam estar disponíveis no computador que inicia a compilação. Cada colaborador deve obter credenciais autorizadas ou configurar seu próprio certificado e a restrição correspondente no Google Cloud.

Para um ambiente já autenticado e configurado:

```bash
pnpm --package=eas-cli dlx eas build --platform android --profile development
```

A compilação em nuvem envia o código e as credenciais necessárias ao EAS. Não publique a chave em exemplos, capturas ou arquivos versionados. A restrição por API, pacote e certificado é necessária porque a chave de Maps é incorporada ao aplicativo Android.

O carregamento do mapa no APK próprio está em validação. O app Google Maps funcionar no aparelho não comprova que a chave usada pelo RolêOut esteja configurada corretamente.

## Estrutura

| Caminho | Responsabilidade |
| :--- | :--- |
| `src/app/` | Rotas: entrada, autenticação, abas, preferências, roteiros, organizações e revisão |
| `src/` | Componentes, telas compartilhadas, sessão, cliente HTTP e regras de planejamento |
| `assets/` | Ícones e recursos visuais |
| `tests/` | Testes de planejamento e afinidades |
| `app.json` | Identidade, pacote Android e plugins |
| `app.config.js` | Configuração do Maps por ambiente |
| `eas.json` | Perfil de geração do APK |

## Verificação

```bash
pnpm typecheck
pnpm lint
pnpm test
```

A exportação dos bundles pode verificar o empacotamento JavaScript:

```bash
pnpm exec expo export --platform all
```

Isso não gera APK/IPA nem valida permissões, GPS ou mapas no aparelho. Para homologação, siga os critérios em [Etapas](../docs/etapas.md).

## Dados e comportamento

- Cadastro e login usam a API Java real; erros de conexão não são substituídos por dados fictícios.
- A demonstração é separada, identificada e disponível apenas em desenvolvimento.
- SecureStore guarda o token nativo; a senha não é persistida no app.
- Localização é solicitada em primeiro plano, sem rastreamento em segundo plano.
- Rolês próximos exigem local publicado, organização aprovada e coordenadas cadastradas.
- HTTP é reservado ao desenvolvimento. O cliente exige HTTPS fora dele.

## Próxima distribuição

Para convidados testarem em outra rede, é necessário hospedar API/banco e configurar um APK com código embarcado. O perfil atual de desenvolvimento não atende sozinho a esse cenário. Testes e distribuição iOS continuam pendentes.
