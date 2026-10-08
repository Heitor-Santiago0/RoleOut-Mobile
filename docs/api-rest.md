# Primeira API de consulta

A API foi adicionada ao WAR existente. As páginas JSP e os controllers `/roles`
e `/sugestoes` continuam funcionando com suas rotas originais.
Prefixo com o contexto atual: `http://localhost:8080/RoleOut/api/v1`.

| Método | Caminho | Comportamento |
| --- | --- | --- |
| GET | /roles | Lista paginada; filtros opcionais categoria, cidade, valorMaximo |
| GET | /roles/{id} | Detalhe público ou 404 |
| GET | /sugestoes | Lista paginada; categoria, cidade e valorMaximo obrigatórios |

Paginação: `limite` padrão 20, de 1 a 100; `deslocamento` padrão 0, de 0 a 100000.
O orçamento deve ser finito e não negativo. Filtros de texto vazios são inválidos.
Sem orçamento, ordenar por data e ID; com orçamento, por valor e ID.
Categoria/cidade usam comparação sem diferença de maiúsculas/minúsculas como
na versão web. A resposta não inclui total de registros nem promete snapshot
entre páginas; usar o tamanho de `itens` para identificar o fim da listagem.

Exemplo: `/roles?cidade=Uberaba&limite=20&deslocamento=0`.

```json
{
  "itens": [
    {
      "id": 1,
      "nome": "Visita ao museu",
      "descricao": "Exemplo de desenvolvimento",
      "local": "Museu",
      "cidade": "Uberaba",
      "data": "2026-10-08",
      "categoria": "Cultura",
      "valor": 20.0
    }
  ],
  "limite": 20,
  "deslocamento": 0
}
```

Datas são ISO 8601 ou null. O DTO usa BigDecimal para produzir JSON numérico,
mas o modelo/DAO legado ainda usa double; converter toda a camada monetária
para BigDecimal é trabalho posterior. O novo banco armazena DECIMAL(10,2).

Erros: JSON com `codigo` e `mensagem`. HTTP 400 para parâmetro inválido, 404 para
detalhe ausente/não publicado, 503 para falha JDBC e 500 para falha inesperada.
Detalhes de SQL e conexão não aparecem no corpo; são registrados no servidor.
A API não oferece operações de escrita nesta etapa.

A API exige o esquema V001 + V002 (inclusive tabelas de locais/organizações).
Rolês vinculados só aparecem quando local=PUBLICADO e organização=APROVADA.
Rolês de teste legados sem local_id aparecem apenas como compatibilidade atual;
limpar/separar esses dados antes de disponibilizar o projeto ao público.

Contrato: `docs/openapi.yaml`. Testes usam servlets com requisição/resposta
simuladas e banco H2; teste HTTP real com Tomcat ainda está pendente.

## Proximidade

GET /api/v1/roles/proximos?latitude=-19.75&longitude=-47.93&raioKm=5
Retorna itens com id, nome, categoria, latitude, longitude e distanciaKm.
Raio permitido: 0,1 a 20 km; padrão 5. Limite fixo 100, ordenado pela distância
geográfica (não distância de trajeto). Apenas locais publicados de organizações
aprovadas com coordenadas cadastradas; registros legados sem local_id não aparecem.
31 testes de backend passaram, incluindo distância, aprovação e parâmetros.
Validação real MySQL/Tomcat e GPS no aparelho continuam pendentes.
