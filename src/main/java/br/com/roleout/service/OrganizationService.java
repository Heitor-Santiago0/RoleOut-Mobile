package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.OrganizationDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrganizationService implements br.com.roleout.model.OrganizationData {
    private final SqlAccess sql;
    public OrganizationService() { this(new SqlAccess()); }
    public OrganizationService(SqlAccess sql) { this.sql = sql; }

    public Organization create(int accountId, OrganizationInput input) {
        OrganizationInput validated = validate(input);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                var dao=new OrganizationDAO(c);
                int id=dao.insertOrganization(validated);dao.addOwner(id,accountId);
                c.commit();
                return new Organization(id, validated.nome(), validated.tipo(), validated.descricao(),
                        validated.contatoEmail(), validated.contatoTelefone(), "PENDENTE", "RESPONSAVEL");
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Organization creation failed.", e); }
    }

    public List<Organization> list(int accountId) {
        try (var c = sql.connection()) {return new OrganizationDAO(c).list(accountId);
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
                new OrganizationDAO(c).updateOrganization(id,value);
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
                int id=new OrganizationDAO(c).saveVenue(organizationId,venueId,v);
                if(id==0)throw notFound();
                c.commit();
                return new Venue(id, organizationId, v.nome(), v.categoria(), v.endereco(), v.cidade(),
                        v.uf(), v.cep(), v.descricao(), v.acessibilidade(), "RASCUNHO", v.latitude(), v.longitude());
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Venue save failed.", e); }
    }

    public List<Venue> listVenues(int accountId, int organizationId) {
        try (var c = sql.connection()) {
            member(c, accountId, organizationId, false);
            return new OrganizationDAO(c).venues(organizationId);
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
                new OrganizationDAO(c).submitVenue(org,id);
                var saved = venue(c, org, id); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Venue submission failed.", e); }
    }
    public List<Review> reviews(int account, int org) {
        try (var c = sql.connection()) {
            member(c, account, org, false);
            return new OrganizationDAO(c).reviews(org);
        } catch (SQLException e) { throw new RuntimeException("Review history failed.", e); }
    }
    private Organization member(Connection c,int account,int org,boolean lock)throws SQLException {
        var found=new OrganizationDAO(c).member(account,org,lock);if(found==null)throw notFound();return found;
    }
    private Venue venue(Connection c,int org,int id)throws SQLException {
        var found=new OrganizationDAO(c).venue(org,id,false);if(found==null)throw notFound();return found;
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
        if ((v.latitude() == null) != (v.longitude() == null)) throw new IllegalArgumentException("Informe latitude e longitude juntas.");
        BigDecimal latitude = coordinate(v.latitude(), 90), longitude = coordinate(v.longitude(), 180);
        return new VenueInput(Inputs.required(v.nome(), 200), Inputs.required(v.categoria(), 100),
                Inputs.required(v.endereco(), 255), Inputs.required(v.cidade(), 150), state, cep,
                Inputs.optional(v.descricao(), 4000), Inputs.optional(v.acessibilidade(), 2000), latitude, longitude);
    }
    private BigDecimal coordinate(BigDecimal value, int limit) {
        if (value == null) return null;
        if (value.abs().compareTo(BigDecimal.valueOf(limit)) > 0) throw new IllegalArgumentException("Coordenada fora do intervalo permitido.");
        try { return value.setScale(6, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Use até seis casas decimais nas coordenadas."); }
    }
    private ApiException notFound() { return new ApiException(404, "NAO_ENCONTRADO", "Cadastro não encontrado."); }
    private ApiException forbidden() { return new ApiException(403, "SEM_PERMISSAO", "Operação não permitida para este perfil."); }
    private ApiException conflict() { return new ApiException(409, "ESTADO_INVALIDO", "O estado atual não permite esta edição."); }
}
