# Recomendações por características

Entrega local de 20/10/2026. Homologação no celular pendente, conforme combinado.

## Contrato e persistência

Campo opcional `caracteristicas` em rascunhos, revisão, detalhes/listagem públicos e pontos próximos:

```json
{"musica":["Rock"],"comida":["Japonesa"],"ambientes":["Tranquilo"]}
```

As opções vêm de GET /api/v1/preferencias, compartilhadas com o perfil do usuário. Apenas valores desse vocabulário são aceitos; duplicatas, nulos dentro das listas e valores desconhecidos são rejeitados. Grupos ausentes viram listas vazias. Não se impõe categoria exclusiva: um bar pode oferecer culinária e música.

V023 adiciona `roles.caracteristicas`; V024 adiciona `rascunhos_roles.caracteristicas`. Ambas são colunas TEXT anuláveis e preservam registros. Aplicar as migrações antes do WAR em banco existente; não recriar volumes. Novos bancos usam a sequência completa.

Omitir o objeto ou enviá-lo nulo na edição preserva as características atuais para compatibilidade com clientes antigos. Para apagar, enviar o objeto com listas vazias. A alteração só afeta o catálogo após aprovação. Revisões guardam antes/depois; rejeições não mudam o público.

## Seleção

Respeita cancelamento, datas, horários e raio de 5 km. Primeiro, programação compatível agora; depois, mais tarde; por último, desconhecida. Dentro de cada faixa: número de dimensões exatas (música/comida/ambiente), data de hoje, distância, ID. Até três resultados, sem duplicatas. Não se trata de probabilidade ou garantia de disponibilidade; usa o horário local do aparelho e os dados publicados. Fuso por estabelecimento ainda pendente.

Categorias amplas continuam como alternativa quando faltam dados, com explicação. Gênero/cozinha conhecidos sem correspondência não recebem justificativa de gosto musical/culinário. Outros interesses independentes podem justificar a sugestão. Não há inferência a partir de descrição/imagem nem alteração dos interesses do usuário.

## Validação local concluída

- Backend: `mvn -B -o package`, 131 testes aprovados, incluindo validação das seleções, desserialização na API, isolamento até aprovação, rejeição, histórico, limpeza explícita e preservação para clientes antigos.
- Aplicativo: 74 testes aprovados, incluindo correspondência específica, fallback explicado, prioridade de horários, deduplicação e exclusão de cancelados/encerrados/distantes. TypeScript e lint aprovados.
- Migrações V023/V024 aplicadas ao banco local de integração e WAR atualizado. Contagens preservadas: 22 rolês, 6 rascunhos e 11 roteiros.
- API local respondeu com seis pontos; campo `caracteristicas` conferido nos pontos e no detalhe público. Dados antigos permanecem nulos, sem preenchimento inventado.

## Testes no celular — executar depois

- [ ] Responsável: abrir/criar rascunho; selecionar Rock, Japonesa e Tranquilo; salvar, reabrir e conferir seleção.
- [ ] Desmarcar opções e conferir listas vazias; repetir com falha de rede ao carregar opções, preservando seleção anterior.
- [ ] Administrador: comparar proposta com publicação atual; rejeitar e conferir que público não muda; reenviar e aprovar.
- [ ] Detalhe público: conferir características aprovadas e Não informado para grupos vazios.
- [ ] Usuário: selecionar os mesmos interesses e verificar explicação no cartão; trocar para outro gênero/cozinha e conferir mudança.
- [ ] Comparar evento de agora com outro mais tarde; um gosto exato futuro não deve superar evento compatível agora.
- [ ] Conferir texto longo, fonte ampliada, leitores de tela e seleção dos chips no Samsung.
- [ ] Confirmar mapa, categorias, planejamento, roteiros offline e notificações existentes continuam funcionando.

Antes de retomar no telefone, ajustar o endereço da API/Metro à rede atual ou configurar USB. O ambiente desta entrega permanece local, sem hospedagem nova.
