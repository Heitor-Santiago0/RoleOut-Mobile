package br.com.roleout.model;
import java.math.BigDecimal;
public interface OrganizationData {
    public record OrganizationInput(String nome, String tipo, String descricao,
                                    String contatoEmail, String contatoTelefone) { }
    public record Organization(int id, String nome, String tipo, String descricao,
                               String contatoEmail, String contatoTelefone, String status, String papel) { }
    public record VenueInput(String nome, String categoria, String endereco, String cidade,
                             String uf, String cep, String descricao, String acessibilidade,
                             BigDecimal latitude, BigDecimal longitude) {
        public VenueInput(String nome, String categoria, String endereco, String cidade,
                          String uf, String cep, String descricao, String acessibilidade) {
            this(nome, categoria, endereco, cidade, uf, cep, descricao, acessibilidade, null, null);
        }
    }
    public record Venue(int id, int organizacaoId, String nome, String categoria, String endereco,
                        String cidade, String uf, String cep, String descricao, String acessibilidade, String status,
                        BigDecimal latitude, BigDecimal longitude) { }
    public record Review(int id, Integer localId, String decisao, String justificativa, String criadoEm) {}
}
