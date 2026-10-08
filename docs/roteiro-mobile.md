# Roteiro personalizado — primeiro incremento

A aba Criar rolê evolui para duas etapas, inspiradas no Figma: cidade, data,
partida e duração; depois pessoas, categorias e orçamento total do grupo.
Ambientes e preferências mais específicas dependem de metadados ainda ausentes
no cadastro; não são usados como filtros fictícios.

A consulta reaproveita GET /api/v1/roles e considera até 100 opções da cidade
dentro do preço máximo por pessoa. Categoria e data são filtradas no app neste
incremento. Rolês com data cadastrada diferente da data escolhida são excluídos.
Sem data, a opção aparece com disponibilidade a confirmar. Preço e ID definem
ordem estável. O limite da consulta é informado quando alcançado.

O usuário monta a sequência, acrescenta/remove paradas, move-as e ajusta o tempo
em intervalos de 15 minutos (mínimo 15, início 60). A soma das visitas precisa
caber na duração escolhida. Horários são estimativas sequenciais, inclusive
com indicação de virada de dia; não comprovam funcionamento ou disponibilidade.
Não há cálculo de deslocamento, reserva, otimização automática nem serviço de IA.

O custo é calculado em centavos: preço cadastrado por pessoa × participantes ×
paradas selecionadas. O orçamento limita o total, não cada atividade isolada.
Transporte, consumo extra e descontos não estão incluídos. A base web não tem
modelo de preços por grupo; essa premissa precisa ser confirmada no cadastro real.

O roteiro é mantido em memória durante a sessão; voltar dos detalhes preserva
as escolhas. Nova busca substitui a seleção, com aviso. Logout ou recarregamento
descartam alterações não salvas. Salvar e recuperar por usuário foi acrescentado; consulte roteiros-salvos.md.
Nenhuma tabela ou tela web foi alterada nesta etapa.

Validação: seis testes de lógica (datas, centavos, candidatos, limites, ordem e
virada de dia), TypeScript e lint. Fluxo de demonstração conferido no navegador
390×844: duas etapas, duas paradas, orçamento excedido, ordenação e retorno dos
detalhes sem perda. API real e Android/iOS no aparelho permanecem pendentes.

## Ajuste de orçamento e data

O orçamento pode ser definido por pessoa (mesmo limite para todos) ou como total
do grupo. No primeiro modo, o limite total é valor informado × participantes;
a consulta e a soma das paradas usam esse limite. O resumo mostra custo por pessoa,
total, limite do grupo e saldo. Não há valores individuais diferentes por participante.

O campo de data aceita somente os números e insere DD/MM/AAAA automaticamente,
inclusive ao colar. Datas inexistentes continuam sendo recusadas. Oito testes de
lógica passaram, incluindo máscara e conversão de orçamento; TypeScript/lint e
os dois ajustes foram conferidos na prévia do navegador.
