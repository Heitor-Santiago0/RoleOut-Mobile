package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RoleReviewService {
    public record Item(RoleDraftService.Draft role, OrganizationService.Organization organizacao, OrganizationService.Venue local) {}
    public record Decision(Integer rascunhoId, Integer versao, String decisao, String justificativa) {}
    private final SqlAccess sql;
    public RoleReviewService() { this(new SqlAccess()); }
    public RoleReviewService(SqlAccess sql) { this.sql = sql; }

    public List<Item> pending(int account, int offset) {
        if (offset < 0 || offset > 100000) throw new IllegalArgumentException("Página inválida.");
        try (var c = sql.connection()) {
            admin(c, account, false);
            var ids = new ArrayList<Integer>();
            try (var s = c.prepareStatement("SELECT id FROM rascunhos_roles WHERE status='PENDENTE' ORDER BY id LIMIT 20 OFFSET ?")) {
                s.setInt(1, offset); try (var rs = s.executeQuery()) { while (rs.next()) ids.add(rs.getInt(1)); }
            }
            var result = new ArrayList<Item>();
            for (int id : ids) result.add(item(c, id, false));
            return result;
        } catch (SQLException e) { throw new RuntimeException("Role review queue failed.", e); }
    }
    public Item decide(int account, Decision input) {
        if (input == null || input.rascunhoId() == null || input.rascunhoId() < 1 || input.versao() == null || input.versao() < 1
                || !Set.of("APROVAR", "REJEITAR").contains(input.decisao() == null ? "" : input.decisao())) throw new IllegalArgumentException("Decisão inválida.");
        String reason = Inputs.required(input.justificativa(), 2000);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                admin(c, account, true);
                Item current = item(c, input.rascunhoId(), true);
                try (var s = c.prepareStatement("SELECT 1 FROM membros_organizacao WHERE conta_id=? AND organizacao_id=?")) {
                    s.setInt(1, account); s.setInt(2, current.organizacao().id());
                    try (var rs = s.executeQuery()) { if (rs.next()) throw forbidden(); }
                }
                var draft = current.role();
                if (!draft.status().equals("PENDENTE") || draft.versao() != input.versao() || draft.roleId() != null) throw conflict();
                boolean approve = input.decisao().equals("APROVAR");
                Integer publicId = null;
                if (approve) {
                    if (!current.organizacao().status().equals("APROVADA") || !current.local().status().equals("PUBLICADO"))
                        throw new ApiException(409, "LOCAL_NAO_APROVADO", "A organização e o local precisam estar aprovados.");
                    try (var s = c.prepareStatement("INSERT INTO roles(nome,descricao,local,cidade,data,categoria,valor,local_id) VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        s.setString(1, draft.nome()); s.setString(2, draft.descricao()); s.setString(3, current.local().endereco()); s.setString(4, current.local().cidade());
                        if (draft.data() == null) s.setNull(5, Types.DATE); else s.setDate(5, Date.valueOf(draft.data()));
                        s.setString(6, draft.categoria()); s.setBigDecimal(7, draft.valor()); s.setInt(8, draft.localId()); s.executeUpdate();
                        try (var keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Missing role ID."); publicId = keys.getInt(1); }
                    }
                }
                try (var s = c.prepareStatement("UPDATE rascunhos_roles SET status=?,role_id=?,versao=versao+1 WHERE id=?")) {
                    s.setString(1, approve ? "PUBLICADO" : "REJEITADO");
                    if (publicId == null) s.setNull(2, Types.INTEGER); else s.setInt(2, publicId);
                    s.setInt(3, draft.id()); s.executeUpdate();
                }
                try (var s = c.prepareStatement("INSERT INTO revisoes_roles(rascunho_id,versao,revisor_conta_id,decisao,justificativa) VALUES (?,?,?,?,?)")) {
                    s.setInt(1, draft.id()); s.setInt(2, draft.versao()); s.setInt(3, account); s.setString(4, input.decisao()); s.setString(5, reason); s.executeUpdate();
                }
                Item saved = item(c, draft.id(), false); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Role review decision failed.", e); }
    }
    private Item item(Connection c, int id, boolean lock) throws SQLException {
        int orgId, venueId;
        try (var s = c.prepareStatement("SELECT l.organizacao_id,l.id FROM rascunhos_roles r JOIN locais l ON l.id=r.local_id WHERE r.id=?")) {
            s.setInt(1, id); try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); orgId = rs.getInt(1); venueId = rs.getInt(2); }
        }
        // Stable associations; all writers lock organization, venue, then draft.
        String suffix = lock ? " FOR UPDATE" : "";
        OrganizationService.Organization org;
        try (var s = c.prepareStatement("SELECT o.*,'' AS papel FROM organizacoes o WHERE id=?" + suffix)) {
            s.setInt(1, orgId); try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); org = OrganizationService.organization(rs); }
        }
        OrganizationService.Venue venue;
        try (var s = c.prepareStatement("SELECT * FROM locais WHERE id=?" + suffix)) {
            s.setInt(1, venueId); try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); venue = OrganizationService.venue(rs); }
        }
        try (var s = c.prepareStatement("SELECT * FROM rascunhos_roles WHERE id=?" + suffix)) {
            s.setInt(1, id); try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); return new Item(RoleDraftService.map(rs), org, venue); }
        }
    }
    private void admin(Connection c, int account, boolean lock) throws SQLException {
        try (var s = c.prepareStatement("SELECT administrador FROM contas WHERE id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, account); try (var rs = s.executeQuery()) { if (!rs.next() || !rs.getBoolean(1)) throw forbidden(); }
        }
    }
    private ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Rolê não encontrado."); }
    private ApiException forbidden() { return new ApiException(403, "SEM_PERMISSAO", "A revisão exige um administrador sem vínculo com a organização."); }
    private ApiException conflict() { return new ApiException(409, "REVISAO_DESATUALIZADA", "O rolê mudou ou não está pendente. Atualize a fila."); }
}
