package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RoleReviewService {
    public record Published(String nome, String descricao, String categoria, String data, java.math.BigDecimal valor,
                            String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento, RoleCharacteristics caracteristicas) {}
    public record Item(RoleDraftService.Draft role, OrganizationService.Organization organizacao, OrganizationService.Venue local, Published publicacao) {}
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
                if (!draft.status().equals("PENDENTE") || draft.versao() != input.versao()) throw conflict();
                boolean approve = input.decisao().equals("APROVAR");
                Integer publicId = draft.roleId();
                if (approve) {
                    if (!current.organizacao().status().equals("APROVADA") || !current.local().status().equals("PUBLICADO"))
                        throw new ApiException(409, "LOCAL_NAO_APROVADO", "A organização e o local precisam estar aprovados.");
                    if (publicId == null) {
                        try (var s = c.prepareStatement("INSERT INTO roles(nome,descricao,local,cidade,data,categoria,valor,local_id,horario,horario_fim,data_fim,funcionamento,caracteristicas) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                            s.setString(1, draft.nome()); s.setString(2, draft.descricao()); s.setString(3, current.local().endereco()); s.setString(4, current.local().cidade());
                            if (draft.data() == null) s.setNull(5, Types.DATE); else s.setDate(5, Date.valueOf(draft.data()));
                            s.setString(6, draft.categoria()); s.setBigDecimal(7, draft.valor()); s.setInt(8, draft.localId()); s.setString(9, draft.horario()); s.setString(10, draft.horarioFim()); s.setString(11, draft.dataFim()); s.setString(12, OpeningHours.encode(draft.funcionamento())); s.setString(13, RoleCharacteristics.encode(draft.caracteristicas())); s.executeUpdate();
                            try (var keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Missing role ID."); publicId = keys.getInt(1); }
                        }
                    } else {
                        try (var s = c.prepareStatement("UPDATE roles SET nome=?,descricao=?,categoria=?,data=?,valor=?,horario=?,horario_fim=?,cancelado=?,motivo_cancelamento=?,data_fim=?,funcionamento=?,caracteristicas=? WHERE id=? AND local_id=?")) {
                            s.setString(1, draft.nome()); s.setString(2, draft.descricao()); s.setString(3, draft.categoria());
                            if (draft.data() == null) s.setNull(4, Types.DATE); else s.setDate(4, Date.valueOf(draft.data()));
                            s.setBigDecimal(5, draft.valor()); s.setString(6, draft.horario()); s.setString(7, draft.horarioFim());
                            s.setBoolean(8, draft.cancelado()); s.setString(9, draft.motivoCancelamento()); s.setString(10, draft.dataFim()); s.setString(11, OpeningHours.encode(draft.funcionamento())); s.setString(12, RoleCharacteristics.encode(draft.caracteristicas())); s.setInt(13, publicId); s.setInt(14, draft.localId());
                            if (s.executeUpdate() != 1) throw conflict();
                        }
                    }
                }
                try (var s = c.prepareStatement("UPDATE rascunhos_roles SET status=?,role_id=?,versao=versao+1 WHERE id=?")) {
                    s.setString(1, approve ? "PUBLICADO" : "REJEITADO");
                    if (publicId == null) s.setNull(2, Types.INTEGER); else s.setInt(2, publicId);
                    s.setInt(3, draft.id()); s.executeUpdate();
                }
                try (var s = c.prepareStatement("INSERT INTO revisoes_roles(rascunho_id,versao,revisor_conta_id,decisao,justificativa,conteudo) VALUES (?,?,?,?,?,?)")) {
                    s.setInt(1, draft.id()); s.setInt(2, draft.versao()); s.setInt(3, account); s.setString(4, input.decisao()); s.setString(5, reason); s.setString(6, priorSnapshot(current.publicacao()) + "Proposta analisada:\n" + snapshot(draft)); s.executeUpdate();
                }
                Item saved = item(c, draft.id(), false); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Role review decision failed.", e); }
    }
    private String priorSnapshot(Published previous) {
        if (previous == null) return "Primeira publicação.\n";
        return "Publicação anterior:\nNome: " + previous.nome() + "\nDescrição: " + previous.descricao()
            + "\nCategoria: " + previous.categoria() + "\nData: " + previous.data()
            + "\nInício: " + previous.horario() + "\nTérmino: " + previous.horarioFim() + "\nData de término: " + previous.dataFim() + "\nFuncionamento:\n" + OpeningHours.describe(previous.funcionamento()) + "\nCaracterísticas: " + RoleCharacteristics.describe(previous.caracteristicas())
            + "\nPreço por pessoa: " + previous.valor() + "\nCancelado: " + previous.cancelado()
            + "\nMotivo do cancelamento: " + previous.motivoCancelamento() + "\n\n";
    }
    private String snapshot(RoleDraftService.Draft draft) {
        return "Nome: " + draft.nome() + "\nDescrição: " + draft.descricao()
            + "\nCategoria: " + draft.categoria() + "\nData: " + draft.data()
            + "\nInício: " + draft.horario() + "\nTérmino: " + draft.horarioFim() + "\nData de término: " + draft.dataFim() + "\nFuncionamento:\n" + OpeningHours.describe(draft.funcionamento()) + "\nCaracterísticas: " + RoleCharacteristics.describe(draft.caracteristicas())
            + "\nPreço por pessoa: " + draft.valor()
            + "\nSituação solicitada: " + (draft.cancelado() ? "Cancelado" : "Ativo")
            + "\nMotivo do cancelamento: " + draft.motivoCancelamento();
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
            s.setInt(1, id); try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); var draft = RoleDraftService.map(rs); return new Item(draft, org, venue, published(c, draft.roleId())); }
        }
    }
    private Published published(Connection c, Integer id) throws SQLException {
        if (id == null) return null;
        try (var s = c.prepareStatement("SELECT * FROM roles WHERE id=?")) {
            s.setInt(1, id);
            try (var rs = s.executeQuery()) {
                if (!rs.next()) throw conflict();
                return new Published(rs.getString("nome"), rs.getString("descricao"), rs.getString("categoria"), rs.getString("data"), rs.getBigDecimal("valor"), rs.getString("horario"), rs.getString("horario_fim"), rs.getBoolean("cancelado"), rs.getString("motivo_cancelamento"), rs.getString("data_fim"), OpeningHours.decode(rs.getString("funcionamento")), RoleCharacteristics.decode(rs.getString("caracteristicas")));
            }
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
