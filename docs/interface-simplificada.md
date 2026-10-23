# Interface simplificada — 20/10/2026

A tela Explorar prioriza mapa, sugestões e catálogo da cidade. O cartão do próximo roteiro ficou compacto; os controles do mapa aparecem lado a lado. Explicações do mapa e motivos das recomendações abrem por toque. Horários e estados de erro continuam visíveis.

A troca de cidade abre um campo de busca. Sem cidade identificada, o campo permanece disponível. As categorias aparecem em uma faixa horizontal. No perfil, as configurações de sugestões automáticas e avisos de alterações ficam em seções expansíveis.

Não há migração de banco nem alteração das regras de recomendação nesta entrega.

## Validação

- TypeScript e lint aprovados; 76 testes automatizados existentes passaram.
- Conferência visual no Android pendente: aparelho não detectado via ADB nesta entrega.
- No celular: conferir mapa e seus dois controles, rolar categorias e filtrar, trocar cidade, expandir motivos e configurações do perfil, abrir um local e o próximo roteiro.
- Conferir textos grandes nas opções de acessibilidade e verificar que os controles não se sobrepõem.
