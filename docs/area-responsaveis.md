# Área dos responsáveis — cadastro inicial

Disponível no app em **Conta → Meus locais**, para contas autenticadas.
A demonstração não grava cadastros. As telas são complementares ao Figma,
que não incluía essa área.

Fluxo implementado:

1. Criar uma organização: restaurante, bar, museu, prefeitura ou outro.
2. Informar nome, e-mail de contato, telefone e descrição opcionais.
3. Cadastrar locais vinculados: nome, categoria, endereço, cidade e UF;
   CEP, descrição e acessibilidade opcionais. A máscara do CEP insere o hífen.
4. Listar e editar os cadastros nas situações permitidas pelo servidor.

Organizações nascem PENDENTE. Apenas RESPONSAVEL edita organizações PENDENTE
ou REJEITADA. Membros podem cadastrar/editar locais RASCUNHO ou REJEITADO,
desde que a organização não esteja SUSPENSA. O token determina a conta;
nenhuma tela do responsável pode atribuir status de aprovação ou publicação.

O responsável envia locais RASCUNHO/REJEITADO para análise. Enquanto PENDENTE,
o local não pode ser editado. Administradores conferem a fila em Conta → Analisar
cadastros e aprovam/rejeitam com justificativa obrigatória. Organizações nascem
PENDENTE e entram automaticamente na fila; uma organização rejeitada retorna
à fila quando o responsável salva a correção. Aprovar o local exige organização
APROVADA. Rejeição permite corrigir e reenviar. O histórico fica em Meus locais.
Salvar local não cria automaticamente um evento/rolê, nem fornece coordenadas.
Cadastro de ofertas/eventos e localização do local serão etapas posteriores.

A lista atual exibe até 100 organizações/locais, limite já existente na API;
as telas informam quando alcançam esse número. Falhas de rede em gravações
orientam consultar a lista antes de repetir, pois ainda não há idempotência de POST.

Foi necessário liberar PUT na política CORS das origens locais explicitamente
configuradas. As permissões continuam verificadas no backend. Nenhuma migração
nova ou alteração no banco da instalação antiga foi necessária.

Validação: teste automatizado de preflight PUT e teste HTTP de criação, edição,
leitura, isolamento entre contas e rejeição de status fornecido pelo cliente.
Tipos, lint e bundles mobile também são verificados.

## Revisão administrativa (09/10/2026)

`POST /organizacoes/{org}/locais/{id}/enviar` envia para análise.
`GET /organizacoes/{org}/revisoes` retorna as últimas 100 decisões aos membros.
`GET /revisoes?deslocamento=0` retorna até 20 itens pendentes para administradores.
`POST /revisoes` recebe organizacaoId, localId (null para organização), versao,
decisao (APROVAR/REJEITAR) e justificativa de até 2.000 caracteres.

O servidor autentica e confere o papel atual no banco. Membros de uma organização
não podem revisá-la, mesmo se administradores. Cada item carrega um hash dos dados
exibidos; mudanças exigem atualização da revisão. Decisão, mudança de status e
histórico são gravados na mesma transação, com bloqueio da organização e local.
Repetir uma decisão ou usar dados desatualizados retorna 409, sem duplicar histórico.

A conta pública nunca recebe administrador pelo cadastro. O operador deve provisionar
uma conta administrativa específica fora da API, após conferir sua identidade.
Nenhuma conta do usuário foi promovida. O teste HTTP cria sua própria conta
temporária no banco isolado e revoga o privilégio ao terminar.

Validação: 56 testes Java, incluindo isolamento, vínculo do revisor, concorrência,
dados desatualizados, rejeição/reenvio e visibilidade de rolês vinculados somente
depois das duas aprovações. Integração MySQL/Tomcat aprovada para envio, revisão,
histórico e conflitos. Tipos, lint e bundles web/Android/iOS aprovados.
No navegador, foram verificados cadastro/login, pular preferências (inclusive
persistência no próximo login), criação de organização/local, envio e logout.
Após envio, o status mudou para Aguardando análise e a edição foi ocultada.
Todos esses registros são fictícios do volume de integração.

Ainda pendentes: suspensão/reativação pela interface, edição de locais após publicação,
execução em aparelho e revisão visual da fila
administrativa. O teste HTTP cobre o fluxo administrativo, mas não substitui
a inspeção visual dessa tela.

## Posição do local

O cadastro de locais aceita `latitude` e `longitude` opcionais. As duas devem ser
informadas juntas ou ambas nulas, com até seis casas decimais; latitude entre -90 e
90, longitude entre -180 e 180. Zero é uma coordenada válida. São utilizadas as
colunas existentes em V002; não é necessária uma nova migração.

O formulário aceita ponto ou vírgula decimal. Também permite solicitar a posição
atual do aparelho, em primeiro plano e somente após o toque do responsável. O
resultado exige precisão conhecida de até 100 metros e confirmação de que o
responsável está no estabelecimento. A posição só é enviada ao salvar o local.
Não há geocodificação automática, coordenadas inventadas ou coleta em segundo plano.

As coordenadas aparecem para o administrador e integram a versão dos dados
revisados. Continuam sujeitas ao mesmo bloqueio de edição dos locais pendentes e
publicados. Locais sem posição continuam disponíveis na busca por cidade após a
publicação de seus rolês, mas não na consulta de proximidade. A busca próxima usa
a posição aprovada do local e exige organização e local aprovados.

O fluxo de criação, revisão e publicação dos rolês está descrito em
[Rascunhos e publicação de rolês](rascunhos-roles.md). A captura por GPS e o mapa
nativo ainda precisam de validação em aparelho real; exportar os bundles não
substitui esse teste.
