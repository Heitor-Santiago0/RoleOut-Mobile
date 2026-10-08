# Cadastro de organizações e locais

Decisão confirmada: publicação depende de aprovação administrativa.
Os rolês da versão web são dados de teste. Não serão tratados como cadastros
reais de organizações nem usados para atribuir propriedade a responsáveis.

## Quem cadastra o quê

- **Conta:** pessoa que entra no sistema. Pode explorar rolês e também participar
  de uma organização. A mesma pessoa não precisa de dois logins.
- **Organização:** restaurante, bar, museu, prefeitura ou outra entidade.
  Uma organização pode ter vários responsáveis/editores e vários locais.
- **Local:** espaço físico, com endereço, categoria, acessibilidade e localização.
  Uma prefeitura pode cadastrar um museu, uma praça e um centro cultural.
- **Rolê:** atividade ou evento que pode ocorrer em um local. O texto `local`
  antigo permanece compatível com a versão web; `local_id` permite associação
  estruturada posteriormente.

## Fluxo proposto para o responsável

1. Criar conta, confirmar e-mail e escolher “Cadastrar minha organização”.
2. Preencher nome, tipo, descrição e contato da organização. Não exigir CNPJ
   como único identificador: instituições e espaços podem ter estruturas distintas.
3. Criar um ou mais locais em rascunho: nome, endereço, cidade/UF, categoria,
   descrição e acessibilidade. Fotos e horários entram em uma etapa posterior.
4. Enviar para análise. Ver o estado do pedido e eventual justificativa.
5. Após aprovação, gerenciar locais e cadastrar atividades/eventos próprios.

Telas que precisam complementar o Figma: escolha de uso (explorar ou representar
organização), cadastro da organização, meus locais, formulário/detalhes do local,
envio e acompanhamento da análise e painel administrativo de análise.
Recomendação: painel web responsivo para responsáveis e administradores;
app mobile inicialmente voltado ao público final. Esse painel será adicional
ao projeto web atual. A escolha de plataforma para o painel ainda pode ser ajustada.

## Aprovação e permissões

Organização: PENDENTE -> APROVADA ou REJEITADA; uma organização aprovada pode
ser SUSPENSA. Local: RASCUNHO -> PENDENTE -> PUBLICADO ou REJEITADO; um local
publicado pode ser SUSPENSO. Uma rejeição permite corrigir e reenviar.
Alterações públicas relevantes devem passar novamente por análise; na primeira
implementação, o local volta a PENDENTE até a nova aprovação.

Responsável cria e administra a organização, seus membros e seus locais.
Editor edita locais e atividades da organização à qual pertence, sem conceder
permissões. Administrador analisa pedidos e registra decisão e justificativa.
O papel administrativo será atribuído fora do cadastro público, nunca aceito
como campo enviado pelo cliente. O futuro serviço deve verificar associação
à organização em cada operação e realizar aprovação/auditoria em uma transação.

Um local só aparece no Explorar quando estiver PUBLICADO **e** sua organização
estiver APROVADA. Suspender a organização oculta seus locais e rolês vinculados.
As consultas REST de rolês já aplicam essa regra para registros com `local_id`.
Registros legados sem vínculo ainda aparecem no ambiente de desenvolvimento;
remover/separar os dados de teste antes de disponibilizar a API ao público.

## Entregue nesta etapa e trabalho seguinte

V002 prepara contas, organizações, membros, locais, revisões e o vínculo dos rolês.
V003 acrescenta sessões e o papel administrativo com padrão FALSE. Login,
cadastro REST em rascunho e autorização por organização estão implementados;
criar uma organização associa automaticamente o criador como responsável.
Detalhes em `login-e-rascunhos.md`. Ainda não há formulário, gestão de membros,
API pública de locais ou serviço de aprovação.

Próximos incrementos desse fluxo: telas do painel, confirmação de e-mail e envio
para análise; depois aprovação, auditoria e consultas públicas de locais.
O primeiro fluxo do app pode ser desenvolvido em paralelo temporal a esses
incrementos, usando os dados de teste identificados como demonstração.
A ordem geral do projeto está em `mvp-academico.md`.
