package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Private drafts remain separate from legacy/public roles until reviewed. */
public class RoleDraftService {
    public record Input(String nome, String descricao, String categoria, String data,
                        BigDecimal valor, Integer versao) {}
    public record Draft(int id, int localId, String nome, String descricao, String categoria,
                        String data, BigDecimal valor, int versao) {}
    private final SqlAccess sql;
    public RoleDraftService() { this(new SqlAccess()); }
    public RoleDraftService(SqlAccess sql) { this.sql = sql; }

    public List<Draft> list(int account, int venue, int offset) {
        if (offset < 0 || offset > 100000) throw new IllegalArgumentException("Página inválida.");
        try (var c = sql.connection()) {
            authorize(c, account, venue, false);
            try (var s = c.prepareStatement("SELECT * FROM rascunhos_roles WHERE local_id=? ORDER BY id LIMIT 20 OFFSET ?")) {
                s.setInt(1, venue); s.setInt(2, offset);
                var result = new ArrayList<Draft>();
                try (var rs = s.executeQuery()) { while (rs.next()) result.add(map(rs)); }
                return result;
            }
        } catch (SQLException e) { throw new RuntimeException("Draft listing failed.", e); }
    }

    public Draft save(int account, int venue, Integer id, Input input) {
        Input v = validate(input, id != null);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                authorize(c, account, venue, true);
                if (id != null) {
                    var current = find(c, venue, id);
                    if (current.versao() != v.versao()) throw conflict("O rascunho mudou. Recarregue antes de salvar.");
                }
                String query = id == null
                        ? "INSERT INTO rascunhos_roles(nome,descricao,categoria,data,valor,local_id) VALUES (?,?,?,?,?,?)"
                        : "UPDATE rascunhos_roles SET nome=?,descricao=?,categoria=?,data=?,valor=?,versao=versao+1 WHERE local_id=? AND id=? AND versao=?";
                try (var s = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, v.nome()); s.setString(2, v.descricao()); s.setString(3, v.categoria());
                    if (v.data() == null) s.setNull(4, Types.DATE); else s.setDate(4, Date.valueOf(v.data()));
                    s.setBigDecimal(5, v.valor()); s.setInt(6, venue);
                    if (id != null) { s.setInt(7, id); s.setInt(8, v.versao()); }
                    if (s.executeUpdate() != 1) throw conflict("O rascunho mudou. Recarregue antes de salvar.");
                    if (id == null) {
                        try (var keys = s.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Missing draft ID.");
                            id = keys.getInt(1);
                        }
                    }
                }
                var saved = find(c, venue, id); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Draft save failed.", e); }
    }

    private void authorize(Connection c, int account, int venue, boolean write) throws SQLException {
        // Lock in the same organization -> venue order used by administrative review.
        try (var s = c.prepareStatement("SELECT o.status FROM organizacoes o JOIN membros_organizacao m ON m.organizacao_id=o.id WHERE m.conta_id=? AND o.id=(SELECT organizacao_id FROM locais WHERE id=?)" + (write ? " FOR UPDATE" : ""))) {
            s.setInt(1, account); s.setInt(2, venue);
            try (var rs = s.executeQuery()) {
                if (!rs.next()) throw missing();
                if (write && !rs.getString(1).equals("APROVADA")) throw conflict("A organização precisa estar aprovada.");
            }
        }
        if (write) {
            try (var s = c.prepareStatement("SELECT status FROM locais WHERE id=? FOR UPDATE")) {
                s.setInt(1, venue);
                try (var rs = s.executeQuery()) {
                    if (!rs.next()) throw missing();
                    if (!rs.getString(1).equals("PUBLICADO")) throw conflict("O local precisa estar aprovado.");
                }
            }
        }
    }
    private Draft find(Connection c, int venue, int id) throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM rascunhos_roles WHERE id=? AND local_id=?")) {
            s.setInt(1, id); s.setInt(2, venue);
            try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); return map(rs); }
        }
    }
    private Draft map(ResultSet rs) throws SQLException {
        var date = rs.getDate("data");
        return new Draft(rs.getInt("id"), rs.getInt("local_id"), rs.getString("nome"),
                rs.getString("descricao"), rs.getString("categoria"), date == null ? null : date.toLocalDate().toString(),
                rs.getBigDecimal("valor"), rs.getInt("versao"));
    }
    private Input validate(Input v, boolean edit) {
        if (v == null || v.valor() == null || v.valor().signum() < 0 || v.valor().compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Informe um valor válido por pessoa.");
        BigDecimal money;
        try { money = v.valor().setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Use até duas casas decimais."); }
        if (edit ? v.versao() == null || v.versao() < 1 : v.versao() != null)
            throw new IllegalArgumentException("Versão inválida.");
        String date = Inputs.optional(v.data(), 10);
        if (date != null) {
            try {
                if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(date).getYear() < 1000)
                    throw new IllegalArgumentException("Data inválida.");
            } catch (DateTimeParseException e) { throw new IllegalArgumentException("Data inválida."); }
        }
        return new Input(Inputs.required(v.nome(), 200), Inputs.optional(v.descricao(), 4000),
                Inputs.required(v.categoria(), 100), date, money, v.versao());
    }
    private ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Cadastro não encontrado."); }
    private ApiException conflict(String message) { return new ApiException(409, "ESTADO_INVALIDO", message); }
}
