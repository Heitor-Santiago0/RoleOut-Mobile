# Etapas do projeto

[← Apresentação](../README.md) · [Funcionalidades](funcionalidades.md)

Situação de referência: **12 de outubro de 2026**, implementação em `develop/api-pool`.

O desenvolvimento é organizado por entregas verificáveis. Uma funcionalidade implementada ainda pode precisar de homologação visual, teste no dispositivo ou preparação operacional. Por isso, esta página usa marcos e critérios em vez de um percentual de conclusão.

## 1 · Fundação da API e dos dados — implementada

**Objetivo:** fornecer uma base de persistência e serviços para o aplicativo.

**Entregas:** API REST em Java; camadas de regras de negócio e acesso SQL; MySQL; gerenciador Singleton de DataSource; pool HikariCP com limites e timeout; scripts SQL versionados; ambiente isolado MySQL/Tomcat.

**Verificação disponível:** testes Java com H2 e servlets simulados; roteiro HTTP contra MySQL/Tomcat. A capacidade sob carga ainda precisa de medição própria.

## 2 · Conta e preferências — implementadas

**Objetivo:** identificar o usuário e guardar suas escolhas.

**Entregas:** cadastro, login, sessões revogáveis, armazenamento nativo do token, edição do nome e onboarding opcional nos quatro grupos de interesses.

**Para concluir a homologação:** verificar restauração de sessão, expiração, saída e preferências após fechar e reabrir o app no aparelho. Recuperação de senha e confirmação de e-mail pertencem à preparação para uso público.

## 3 · Descoberta e detalhes — implementados

**Objetivo:** permitir encontrar e avaliar opções de lazer.

**Entregas:** Explorar, cidade, categorias, paginação, detalhes e destaques por afinidade explicável. Estados de erro, carregamento e ausência de resultados separados.

**Para concluir a homologação:** validar buscas, navegação e comportamento de rede no aparelho com dados identificados como testes.

## 4 · Planejamento e roteiros — implementados

**Objetivo:** transformar opções do catálogo em um passeio organizado.

**Entregas:** data com máscara, participantes, duração, orçamento por pessoa/grupo, seleção e ordenação manual de paradas, estimativas, salvamento, consulta privada e exclusão. O servidor recalcula custos e valida o orçamento.

**Para concluir a homologação:** executar o fluxo completo de montagem, gravação, reabertura e exclusão no celular, incluindo limites, datas e perda de conexão.

## 5 · Responsáveis e publicação — implementados

**Objetivo:** permitir que organizações ofereçam atividades com revisão antes da divulgação.

**Entregas:** cadastro de organização/local, coordenadas, rascunhos de rolês, envio para análise, filas administrativas, decisões justificadas, histórico e controle de concorrência.

**Para concluir a homologação:** verificar todas as telas de administração e repetir no dispositivo o caminho de rejeição, correção, aprovação e aparecimento no catálogo.

## 6 · Android e mapa — em validação

**Objetivo:** validar o aplicativo nativo no aparelho real.

**Entregas:** mapa de proximidade, localização em primeiro plano, ícones da navegação, configuração da chave Google Maps por ambiente, certificado de testes e projeto vinculado ao EAS.

**Situação:** geração do APK de desenvolvimento iniciada. O mapa apresentou falha no Expo Go; a versão própria será usada para validar a configuração nativa.

**Critério de conclusão:** APK instalado no Samsung, mapa carregado, localização autorizada/recusada tratada corretamente, centralização e pontos funcionando. A geração do arquivo, sozinha, não comprova essa entrega.

## 7 · Testes remotos com convidados — planejados

**Objetivo:** permitir que outras pessoas testem sem depender da rede do notebook.

**Entregas previstas:** hospedagem da API e banco, HTTPS, configuração do endereço remoto e APK de testes com código embarcado.

**Critério de conclusão:** convidado instala por link e consegue cadastrar conta, explorar e salvar um roteiro usando outra rede. Definir também coleta de problemas, atualização do APK e separação dos dados fictícios.

## 8 · Preparação para uso público — pendente

**Objetivo:** operar o produto além do ambiente controlado de testes.

**Entregas previstas:** recuperação de senha e confirmação de e-mail; revisão de acesso administrativo; configuração de credenciais de produção; backups e teste de restauração; tratamento de logs e dados de localização; revisão de privacidade; monitoramento e testes de carga compatíveis com o uso esperado.

**Critério de conclusão:** fluxos essenciais homologados, operação documentada e limitações conhecidas avaliadas antes de abrir o cadastro ao público. A publicação em lojas e a validação iOS terão critérios próprios.

## Evoluções de produto a avaliar

Foto/avatar, imagens dos locais, informações detalhadas de funcionamento, edição após publicação, compartilhamento de roteiros, preços individuais por participante e cálculo de trajetos. Esses itens não devem ser apresentados como recursos já entregues.

## Como atualizar esta página

Ao concluir um marco, registrar a evidência de validação, atualizar o catálogo de funcionalidades e remover a pendência correspondente. Exportar bundles, gerar APK e testar no aparelho são verificações diferentes e devem permanecer identificadas.
