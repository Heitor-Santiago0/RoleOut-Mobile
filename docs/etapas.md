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

## Atualização local — 20/10/2026

Recomendações por características declaradas concluídas; agora há sugestões automáticas dentro do Explorar, com consentimento opcional, frequência por conta e escolha sobre outras cidades. A API valida proximidade, aprovação, programação e interesses antes de gerar o cartão. Nenhum serviço foi hospedado. Os testes no celular permanecem adiados.

Próximo passo desta frente: detecção e avisos de viagens em segundo plano, com configuração nativa, controles de privacidade e validação Android. Publicação social e Copiar rolê permanecem posteriores, conforme prioridade definida. Ver [sugestões automáticas](sugestoes-automaticas.md).

## Avisos em viagens — entrega local de 20/10/2026

Adicionado fluxo Android opcional em Perfil → Avisos em viagens, com consentimento por sessão, serviço visível no Android, revalidação da conta e notificação que abre o local. Reutiliza API e frequência existentes; não altera banco ou dados. Necessita novo APK para homologação. [Implementação, limites e checklist](avisos-viagens.md).

Próxima validação desta frente: novo APK e teste em segundo plano. A publicação social e Copiar rolê continuam posteriores. A interface simplificada do Explorar foi confirmada pelo usuário no aparelho nesta data.

## Preferências e frequência — ajuste solicitado

Adicionada tela Perfil → Configurações com sequência de autorizações, situação real das permissões e controles separados. Nova política padrão: três sugestões nas últimas 24 horas; extras só com música, culinária e ambiente correspondentes, intervalo mínimo de uma hora e sem repetir rolê na janela. Configurações anteriores preservadas. V026 aplicada, 143 testes Java e 83 móveis aprovados. Autorizações do sistema continuam dependendo do usuário.

## Confiabilidade das permissões — 22/10/2026

Concluída revisão da ativação centralizada: resultados por recurso, limite de espera do GPS/geocodificador, cancelamento ao sair da tela ou trocar a sessão e confirmação do serviço nativo antes do sucesso. 88 testes móveis, TypeScript e lint aprovados. Banco e API preservados. Novo APK e testes físicos seguem pendentes; roteiro atualizado em [avisos em viagens](avisos-viagens.md).

## Novo APK de avisos de viagem — 22/10/2026

Build Android de desenvolvimento concluído: df2f4e52-a61a-404a-ab5e-38398d253ba5. APK baixado e manifesto conferido: pacote original e permissões de background, serviço de localização e notificações presentes. Instalação como atualização e teste real ainda pendentes. Ver [avisos em viagens](avisos-viagens.md).

## Perfis públicos — entrega local de 22/10/2026

Usuário adiou os demais testes no celular e autorizou seguir. Iniciada a frente social com perfil opt-in, nome público separado, apresentação, publicação/ocultação, visualização e lista paginada de pessoas. Banco V027, API autenticada e telas implementados; dados existentes preservados. 153 testes Java e 90 móveis aprovados, TypeScript/lint e teste HTTP com MySQL/Tomcat concluídos. [Fluxo e testes físicos pendentes](perfis-publicos.md).

Próxima entrega prevista: publicação voluntária de roteiros com relato e privacidade, aproveitando essa identidade; depois feed/fotos/interações e Copiar rolê com revalidação. Ainda não implementados nesta entrega. Testes físicos de permissões/viagens não foram considerados concluídos.


## Adequação ao T2 — 19/11/2026

Concluída separação Controller → Service → DAO na API: consultas e mapeamento no DAO, regras/autorização/transações no Service, transporte HTTP nos Servlets. Modelos compartilhados e vocabulário de preferências em `model/`. Sem migração nova, mudança de endpoint ou remoção de dados. Parte social permanece pausada nesta etapa.

[Guia do trabalho](trabalho-api-t2.md): requisitos, CRUD de roteiros, execução, apresentação e pendências da dupla. Validação: suíte Java, teste de limites entre camadas e CRUD HTTP com MySQL real, incluindo isolamento entre contas e limpeza das fixtures. Testes no celular continuam pendentes: login, Explorar, roteiro/renomeação/exclusão, revisão de horários, aprovação e notificações. As entradas anteriores são registros históricos, não o estado final de todas as funcionalidades.


## Publicações com fotos e relatos — 19/11/2026

Implementada publicação explícita a partir de roteiro próprio: rascunho privado, até três fotos/legendas, relato, prévia, perfil público obrigatório, consulta por outra conta, edição, ocultação e exclusão independente. Snapshot preserva o planejamento privado; fotos validadas/reencodadas sem metadados. V028 adiciona duas tabelas sem alterar cadastros existentes. 168 testes Java, 93 móveis, TypeScript/lint e integração HTTP/MySQL aprovados; bundle Android exportado. Novo APK necessário para o seletor de fotos; todos os testes físicos permanecem anotados em [publicações](publicacoes-roteiros.md).

Próximas frentes: feed e interações, controles de denúncia/moderação, Copiar rolê com revalidação. Não há hospedagem pública nesta entrega.


## Consolidação acadêmica e comunidade — 25/11/2026

Implementados feed público/seguindo, acompanhamento privado de autores, comentários editáveis por autoria, avaliações de visitas autodeclaradas concluídas, denúncias e moderação administrativa. Copiar rolê cria um novo roteiro privado na conta do usuário, personalizável, com revalidação de datas/horários/preços e créditos da origem; não altera o roteiro do autor. V029 adiciona tabelas sem excluir registros. Ver [comunidade](comunidade.md).

PDF T2 original revisado: Java, MVC/Controller-Service-DAO, MySQL, CRUD, documentação, GitHub e apresentação. [Checklist consolidado](entrega-final-t2.md) reúne a validação física pendente e os passos de entrega. Hospedagem e produção pública permanecem fora desta entrega local.

## Feed visual — 26/11/2026

Após confirmar o funcionamento da atualização de perfil, feed e catálogo no celular, o usuário autorizou continuar. Implementados cartões com capa reduzida, resumo curto e contagem de fotos, carregamento por janela, recuperação de falhas e atualização desde o início. Endpoint autenticado de miniaturas aplica as regras de privacidade/moderação existentes; sem nova migração ou mudança nos dados originais.

188 testes Java, 96 mobile, TypeScript, lint de `src/`, exportação Android e integração HTTP/MySQL aprovados. Mantidas 32 contas, 12 roteiros e 25 rolês locais após limpar as fixtures. [Entrega e checklist](feed-visual.md). Ainda requer publicação da API e novo APK; o APK instalado pelo usuário corresponde à etapa anterior.
