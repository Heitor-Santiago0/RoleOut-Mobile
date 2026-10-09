package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.security.Tokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;

public class ReviewService {
    public record Item(int organizacaoId, Integer localId, OrganizationService.Organization organizacao,
                       OrganizationService.Venue local, String versao) {}
    public record Decision(Integer organizacaoId, Integer localId, String versao, String decisao, String justificativa) {}
    private final SqlAccess sql;
    private final ObjectMapper json = new ObjectMapper();
    public ReviewService() { this(new SqlAccess()); }
    public ReviewService(SqlAccess sql) { this.sql = sql; }
    private ApiException forbidden() { return new ApiException(403, "SEM_PERMISSAO", "A revisão exige um administrador sem vínculo com a organização."); }
    private ApiException conflict() { return new ApiException(409, "REVISAO_DESATUALIZADA", "O cadastro mudou ou não está pendente. Atualize e revise novamente."); }
    private void admin(Connection c, int account, boolean lock) throws SQLException {
        try (var s = c.prepareStatement("SELECT administrador FROM contas WHERE id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, account); try (var rs = s.executeQuery()) { if (!rs.next() || !rs.getBoolean(1)) throw forbidden(); }
        }
    }
    private Item item(Connection c, int org, Integer local, boolean lock) throws SQLException {
        OrganizationService.Organization organization;
        try (var s = c.prepareStatement("SELECT o.*, '' AS papel FROM organizacoes o WHERE id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, org); try (var rs = s.executeQuery()) {
                if (!rs.next()) throw new ApiException(404, "NAO_ENCONTRADO", "Cadastro não encontrado.");
                organization = OrganizationService.organization(rs);
            }
        }
        OrganizationService.Venue venue = null;
        if (local != null) {
            try (var s = c.prepareStatement("SELECT * FROM locais WHERE id=? AND organizacao_id=?" + (lock ? " FOR UPDATE" : ""))) {
                s.setInt(1, local); s.setInt(2, org); try (var rs = s.executeQuery()) {
                    if (!rs.next()) throw new ApiException(404, "NAO_ENCONTRADO", "Local não encontrado.");
                    venue = OrganizationService.venue(rs);
                }
            }
        }
        try { return new Item(org, local, organization, venue, Tokens.digest(Base64.getEncoder().encodeToString(json.writeValueAsBytes(Arrays.asList(organization, venue))))); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public List<Item> pending(int account, int offset) {
        if (offset < 0 || offset > 100000) throw new IllegalArgumentException("Invalid offset.");
        try (var c = sql.connection()) {
            admin(c, account, false);
            var keys = new ArrayList<int[]>();
            try (var s = c.prepareStatement("""
                    SELECT id AS org_id, 0 AS local_id FROM organizacoes WHERE status='PENDENTE'
                    UNION ALL SELECT organizacao_id AS org_id, id AS local_id FROM locais WHERE status='PENDENTE'
                    ORDER BY org_id, local_id LIMIT 20 OFFSET ?
                    """)) {
                s.setInt(1, offset); try (var rs = s.executeQuery()) { while (rs.next()) keys.add(new int[]{rs.getInt(1), rs.getInt(2)}); }
            }
            var result = new ArrayList<Item>();
            for (var key : keys) result.add(item(c, key[0], key[1] == 0 ? null : key[1], false));
            return result;
        } catch (SQLException e) { throw new RuntimeException("Review queue failed.", e); }
    }
    public Item decide(int account, Decision input) {
        if (input.organizacaoId() == null || input.organizacaoId() < 1 || (input.localId() != null && input.localId() < 1)
                || input.versao() == null || !input.versao().matches("[a-f0-9]{64}")
                || !Set.of("APROVAR", "REJEITAR").contains(input.decisao() == null ? "" : input.decisao())) throw new IllegalArgumentException("Invalid decision.");
        String reason = Inputs.required(input.justificativa(), 2000);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                admin(c, account, true);
                var current = item(c, input.organizacaoId(), input.localId(), true);
                try (var s = c.prepareStatement("SELECT 1 FROM membros_organizacao WHERE conta_id=? AND organizacao_id=?")) {
                    s.setInt(1, account); s.setInt(2, input.organizacaoId()); try (var rs = s.executeQuery()) { if (rs.next()) throw forbidden(); }
                }
                String status = current.local() == null ? current.organizacao().status() : current.local().status();
                if (!status.equals("PENDENTE") || !current.versao().equals(input.versao())) throw conflict();
                boolean approve = input.decisao().equals("APROVAR");
                if (approve && current.local() != null && !current.organizacao().status().equals("APROVADA"))
                    throw new ApiException(409, "ORGANIZACAO_NAO_APROVADA", "Aprove primeiro a organização.");
                String next = current.local() == null ? (approve ? "APROVADA" : "REJEITADA") : (approve ? "PUBLICADO" : "REJEITADO");
                String query = current.local() == null ? "UPDATE organizacoes SET status=? WHERE id=?" : "UPDATE locais SET status=? WHERE id=?";
                try (var s = c.prepareStatement(query)) { s.setString(1, next); s.setInt(2, current.local() == null ? input.organizacaoId() : input.localId()); s.executeUpdate(); }
                try (var s = c.prepareStatement("INSERT INTO revisoes_cadastro (organizacao_id,local_id,revisor_conta_id,decisao,justificativa) VALUES (?,?,?,?,?)")) {
                    s.setInt(1, input.organizacaoId()); if (input.localId() == null) s.setNull(2, Types.INTEGER); else s.setInt(2, input.localId());
                    s.setInt(3, account); s.setString(4, input.decisao()); s.setString(5, reason); s.executeUpdate();
                }
                var saved = item(c, input.organizacaoId(), input.localId(), false); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Review decision failed.", e); }
    }
}
