package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OrganizationService {
    public record OrganizationInput(String nome, String tipo, String descricao,
                                    String contatoEmail, String contatoTelefone) { }
    public record Organization(int id, String nome, String tipo, String descricao,
                               String contatoEmail, String contatoTelefone, String status, String papel) { }
    public record VenueInput(String nome, String categoria, String endereco, String cidade,
                             String uf, String cep, String descricao, String acessibilidade) { }
    public record Venue(int id, int organizacaoId, String nome, String categoria, String endereco,
                        String cidade, String uf, String cep, String descricao, String acessibilidade, String status) { }
    private final SqlAccess sql;
    public OrganizationService() { this(new SqlAccess()); }
    public OrganizationService(SqlAccess sql) { this.sql = sql; }

    public Organization create(int accountId, OrganizationInput input) {
        OrganizationInput validated = validate(input);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (var s = c.prepareStatement("INSERT INTO organizacoes (nome,tipo,descricao,contato_email,contato_telefone) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    bindOrganization(s, validated); s.executeUpdate();
                    try (var keys = s.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Missing organization ID.");
                        id = keys.getInt(1);
                    }
                }
                try (var s = c.prepareStatement("INSERT INTO membros_organizacao (organizacao_id,conta_id,papel) VALUES (?,?,'RESPONSAVEL')")) {
                    s.setInt(1, id); s.setInt(2, accountId); s.executeUpdate();
                }
                c.commit();
                return new Organization(id, validated.nome(), validated.tipo(), validated.descricao(),
                        validated.contatoEmail(), validated.contatoTelefone(), "PENDENTE", "RESPONSAVEL");
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Organization creation failed.", e); }
    }

    public List<Organization> list(int accountId) {
        try (var c = sql.connection(); var s = c.prepareStatement("SELECT o.*,m.papel FROM organizacoes o JOIN membros_organizacao m ON m.organizacao_id=o.id WHERE m.conta_id=? ORDER BY o.id LIMIT 100")) {
            s.setInt(1, accountId);
            List<Organization> items = new ArrayList<>();
            try (var rs = s.executeQuery()) { while (rs.next()) items.add(organization(rs)); }
            return items;
        } catch (SQLException e) { throw new RuntimeException("Organization listing failed.", e); }
    }

    public Organization find(int accountId, int organizationId) {
        try (var c = sql.connection()) { return member(c, accountId, organizationId, false); }
        catch (SQLException e) { throw new RuntimeException("Organization lookup failed.", e); }
    }

    public Organization update(int accountId, int id, OrganizationInput input) {
        OrganizationInput value = validate(input);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                Organization existing = member(c, accountId, id, true);
                if (!existing.papel().equals("RESPONSAVEL")) throw forbidden();
                if (!Set.of("PENDENTE", "REJEITADA").contains(existing.status())) throw conflict();
                try (var s = c.prepareStatement("UPDATE organizacoes SET nome=?,tipo=?,descricao=?,contato_email=?,contato_telefone=?,status='PENDENTE' WHERE id=?")) {
                    bindOrganization(s, value); s.setInt(6, id); s.executeUpdate();
                }
                c.commit();
                return new Organization(id, value.nome(), value.tipo(), value.descricao(),
                        value.contatoEmail(), value.contatoTelefone(), "PENDENTE", existing.papel());
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Organization update failed.", e); }
    }

    public Venue saveVenue(int accountId, int organizationId, Integer venueId, VenueInput input) {
        VenueInput v = validate(input);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                Organization organization = member(c, accountId, organizationId, true);
                if (organization.status().equals("SUSPENSA")) throw conflict();
                if (venueId != null) {
                    Venue current = venue(c, organizationId, venueId);
                    if (!Set.of("RASCUNHO", "REJEITADO").contains(current.status())) throw conflict();
                }
                String query = venueId == null
                        ? "INSERT INTO locais (nome,categoria,endereco,cidade,uf,cep,descricao,acessibilidade,organizacao_id) VALUES (?,?,?,?,?,?,?,?,?)"
                        : "UPDATE locais SET nome=?,categoria=?,endereco=?,cidade=?,uf=?,cep=?,descricao=?,acessibilidade=?,status='RASCUNHO' WHERE organizacao_id=? AND id=?";
                int id;
                try (var s = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, v.nome()); s.setString(2, v.categoria()); s.setString(3, v.endereco());
                    s.setString(4, v.cidade()); s.setString(5, v.uf()); s.setString(6, v.cep());
                    s.setString(7, v.descricao()); s.setString(8, v.acessibilidade()); s.setInt(9, organizationId);
                    if (venueId != null) s.setInt(10, venueId);
                    if (s.executeUpdate() != 1) throw notFound();
                    if (venueId == null) {
                        try (var keys = s.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Missing venue ID.");
                            id = keys.getInt(1);
                        }
                    } else { id = venueId; }
                }
                c.commit();
                return new Venue(id, organizationId, v.nome(), v.categoria(), v.endereco(), v.cidade(),
                        v.uf(), v.cep(), v.descricao(), v.acessibilidade(), "RASCUNHO");
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Venue save failed.", e); }
    }

    public List<Venue> listVenues(int accountId, int organizationId) {
        try (var c = sql.connection()) {
            member(c, accountId, organizationId, false);
            try (var s = c.prepareStatement("SELECT * FROM locais WHERE organizacao_id=? ORDER BY id LIMIT 100")) {
                s.setInt(1, organizationId);
                List<Venue> items = new ArrayList<>();
                try (var rs = s.executeQuery()) { while (rs.next()) items.add(venue(rs)); }
                return items;
            }
        } catch (SQLException e) { throw new RuntimeException("Venue listing failed.", e); }
    }

    public Venue findVenue(int accountId, int organizationId, int venueId) {
        try (var c = sql.connection()) {
            member(c, accountId, organizationId, false);
            return venue(c, organizationId, venueId);
        } catch (SQLException e) { throw new RuntimeException("Venue lookup failed.", e); }
    }

    public Venue submitVenue(int account, int org, int id) {
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                var organization = member(c, account, org, true);
                if (organization.status().equals("SUSPENSA")) throw conflict();
                var current = venue(c, org, id);
                if (!Set.of("RASCUNHO", "REJEITADO").contains(current.status())) throw conflict();
                try (var s = c.prepareStatement("UPDATE locais SET status='PENDENTE' WHERE id=? AND organizacao_id=?")) {
                    s.setInt(1, id); s.setInt(2, org); s.executeUpdate();
                }
                var saved = venue(c, org, id); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Venue submission failed.", e); }
    }
    public record Review(int id, Integer localId, String decisao, String justificativa, String criadoEm) {}
    public List<Review> reviews(int account, int org) {
        try (var c = sql.connection()) {
            member(c, account, org, false);
            try (var s = c.prepareStatement("SELECT * FROM revisoes_cadastro WHERE organizacao_id=? ORDER BY id DESC LIMIT 100")) {
                s.setInt(1, org); var items = new ArrayList<Review>();
                try (var rs = s.executeQuery()) {
                    while (rs.next()) { int local = rs.getInt("local_id"); Integer localId = rs.wasNull() ? null : local;
                        items.add(new Review(rs.getInt("id"), localId, rs.getString("decisao"), rs.getString("justificativa"), rs.getTimestamp("criado_em").toInstant().toString())); }
                }
                return items;
            }
        } catch (SQLException e) { throw new RuntimeException("Review history failed.", e); }
    }
    private Organization member(Connection c, int account, int organization, boolean lock) throws SQLException {
        try (var s = c.prepareStatement("SELECT o.*,m.papel FROM organizacoes o JOIN membros_organizacao m ON m.organizacao_id=o.id WHERE o.id=? AND m.conta_id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, organization); s.setInt(2, account);
            try (var rs = s.executeQuery()) {
                if (!rs.next()) throw notFound();
                return organization(rs);
            }
        }
    }
    private Venue venue(Connection c, int org, int id) throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM locais WHERE id=? AND organizacao_id=?")) {
            s.setInt(1, id); s.setInt(2, org);
            try (var rs = s.executeQuery()) {
                if (!rs.next()) throw notFound();
                return venue(rs);
            }
        }
    }
    static Organization organization(ResultSet rs) throws SQLException {
        return new Organization(rs.getInt("id"), rs.getString("nome"), rs.getString("tipo"),
                rs.getString("descricao"), rs.getString("contato_email"), rs.getString("contato_telefone"),
                rs.getString("status"), rs.getString("papel"));
    }
    static Venue venue(ResultSet rs) throws SQLException {
        return new Venue(rs.getInt("id"), rs.getInt("organizacao_id"), rs.getString("nome"),
                rs.getString("categoria"), rs.getString("endereco"), rs.getString("cidade"), rs.getString("uf"),
                rs.getString("cep"), rs.getString("descricao"), rs.getString("acessibilidade"), rs.getString("status"));
    }
    private OrganizationInput validate(OrganizationInput v) {
        String type = Inputs.required(v.tipo(), 30).toUpperCase(Locale.ROOT);
        if (!Set.of("RESTAURANTE", "BAR", "MUSEU", "PREFEITURA", "OUTRO").contains(type)) {
            throw new IllegalArgumentException("Tipo de organização inválido.");
        }
        return new OrganizationInput(Inputs.required(v.nome(), 200), type, Inputs.optional(v.descricao(), 4000),
                Inputs.email(v.contatoEmail()), Inputs.optional(v.contatoTelefone(), 30));
    }
    private VenueInput validate(VenueInput v) {
        String state = Inputs.required(v.uf(), 2).toUpperCase(Locale.ROOT);
        if (!Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO").contains(state)) {
            throw new IllegalArgumentException("UF inválida.");
        }
        String cep = Inputs.optional(v.cep(), 9);
        if (cep != null && !cep.matches("[0-9]{5}-?[0-9]{3}")) throw new IllegalArgumentException("CEP inválido.");
        return new VenueInput(Inputs.required(v.nome(), 200), Inputs.required(v.categoria(), 100),
                Inputs.required(v.endereco(), 255), Inputs.required(v.cidade(), 150), state, cep,
                Inputs.optional(v.descricao(), 4000), Inputs.optional(v.acessibilidade(), 2000));
    }
    private void bindOrganization(java.sql.PreparedStatement s, OrganizationInput v) throws SQLException {
        s.setString(1, v.nome()); s.setString(2, v.tipo()); s.setString(3, v.descricao());
        s.setString(4, v.contatoEmail()); s.setString(5, v.contatoTelefone());
    }
    private ApiException notFound() { return new ApiException(404, "NAO_ENCONTRADO", "Cadastro não encontrado."); }
    private ApiException forbidden() { return new ApiException(403, "SEM_PERMISSAO", "Operação não permitida para este perfil."); }
    private ApiException conflict() { return new ApiException(409, "ESTADO_INVALIDO", "O estado atual não permite esta edição."); }
}
