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
nenhuma tela pode atribuir status de aprovação ou publicação.

Esta etapa permite preparar rascunhos. O envio de locais à análise, revisão
administrativa, justificativas e publicação ainda não estão implementados.
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
Tipos, lint e bundles mobile também são verificados. A conferência visual das
novas telas e a execução em aparelho continuam pendentes.
