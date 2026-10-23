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
                        BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento, RoleCharacteristics caracteristicas) {
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, cancelado, motivoCancelamento, dataFim, funcionamento, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, cancelado, motivoCancelamento, dataFim, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, cancelado, motivoCancelamento, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, false, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario) {
            this(nome, descricao, categoria, data, valor, versao, horario, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao) {
            this(nome, descricao, categoria, data, valor, versao, null, null);
        }
    }
    public record Draft(int id, int localId, String nome, String descricao, String categoria,
                        String data, BigDecimal valor, int versao, String status, Integer roleId, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento, RoleCharacteristics caracteristicas) {}
    public record Submission(Integer versao) {}
    public record Review(int id, int versao, String decisao, String justificativa, String criadoEm, String conteudo) {}
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
                RoleCharacteristics characteristics = v.caracteristicas();
                if (id != null) {
                    var current = find(c, venue, id, true);
                    if (characteristics == null) characteristics = current.caracteristicas();
                    if (current.status().equals("PENDENTE")) throw conflict("Aguarde a análise antes de editar.");
                    if (v.cancelado() && current.roleId() == null) throw conflict("Somente eventos publicados podem ser cancelados.");
                    if (current.versao() != v.versao()) throw conflict("O rascunho mudou. Recarregue antes de salvar.");
                }
                if (id == null && v.cancelado()) throw conflict("Somente eventos publicados podem ser cancelados.");
                String query = id == null
                        ? "INSERT INTO rascunhos_roles(nome,descricao,categoria,data,valor,local_id,horario,horario_fim,data_fim,funcionamento,caracteristicas) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
                        : "UPDATE rascunhos_roles SET nome=?,descricao=?,categoria=?,data=?,valor=?,versao=versao+1,status='RASCUNHO',horario=?,horario_fim=?,cancelado=?,motivo_cancelamento=?,data_fim=?,funcionamento=?,caracteristicas=? WHERE local_id=? AND id=? AND versao=?";
                try (var s = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, v.nome()); s.setString(2, v.descricao()); s.setString(3, v.categoria());
                    if (v.data() == null) s.setNull(4, Types.DATE); else s.setDate(4, Date.valueOf(v.data()));
                    s.setBigDecimal(5, v.valor());
                    if (id == null) { s.setInt(6, venue); s.setString(7, v.horario()); s.setString(8, v.horarioFim()); s.setString(9, v.dataFim()); s.setString(10, OpeningHours.encode(v.funcionamento())); s.setString(11, RoleCharacteristics.encode(characteristics)); }
                    else { s.setString(6, v.horario()); s.setString(7, v.horarioFim()); s.setBoolean(8, v.cancelado()); s.setString(9, v.motivoCancelamento()); s.setString(10, v.dataFim()); s.setString(11, OpeningHours.encode(v.funcionamento())); s.setString(12, RoleCharacteristics.encode(characteristics)); s.setInt(13, venue); }
                    if (id != null) { s.setInt(14, id); s.setInt(15, v.versao()); }
                    if (s.executeUpdate() != 1) throw conflict("O rascunho mudou. Recarregue antes de salvar.");
                    if (id == null) {
                        try (var keys = s.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Missing draft ID.");
                            id = keys.getInt(1);
                        }
                    }
                }
                var saved = find(c, venue, id, true); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Draft save failed.", e); }
    }

    public Draft submit(int account, int venue, int id, Submission input) {
        if (input == null || input.versao() == null || input.versao() < 1) throw new IllegalArgumentException("Versão inválida.");
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                authorize(c, account, venue, true);
                var current = find(c, venue, id, true); editable(current);
                if (current.versao() != input.versao()) throw conflict("O rascunho mudou. Recarregue antes de enviar.");
                try (var s = c.prepareStatement("UPDATE rascunhos_roles SET status='PENDENTE',versao=versao+1 WHERE id=?")) {
                    s.setInt(1, id); s.executeUpdate();
                }
                var saved = find(c, venue, id, true); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Draft submission failed.", e); }
    }
    public List<Review> reviews(int account, int venue, int id) {
        try (var c = sql.connection()) {
            authorize(c, account, venue, false); find(c, venue, id, false);
            try (var s = c.prepareStatement("SELECT * FROM revisoes_roles WHERE rascunho_id=? ORDER BY id DESC LIMIT 100")) {
                s.setInt(1, id); var result = new ArrayList<Review>();
                try (var rs = s.executeQuery()) { while (rs.next()) result.add(new Review(rs.getInt("id"), rs.getInt("versao"), rs.getString("decisao"), rs.getString("justificativa"), rs.getTimestamp("criado_em").toInstant().toString(), rs.getString("conteudo"))); }
                return result;
            }
        } catch (SQLException e) { throw new RuntimeException("Draft review history failed.", e); }
    }
    private void editable(Draft draft) {
        if (!draft.status().equals("RASCUNHO") && !draft.status().equals("REJEITADO"))
            throw conflict("O rolê não pode ser alterado enquanto aguarda análise ou após publicação.");
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
    private Draft find(Connection c, int venue, int id, boolean lock) throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM rascunhos_roles WHERE id=? AND local_id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setInt(1, id); s.setInt(2, venue);
            try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); return map(rs); }
        }
    }
    static Draft map(ResultSet rs) throws SQLException {
        var date = rs.getDate("data");
        int role = rs.getInt("role_id"); Integer roleId = rs.wasNull() ? null : role;
        return new Draft(rs.getInt("id"), rs.getInt("local_id"), rs.getString("nome"),
                rs.getString("descricao"), rs.getString("categoria"), date == null ? null : date.toLocalDate().toString(),
                rs.getBigDecimal("valor"), rs.getInt("versao"), rs.getString("status"), roleId, rs.getString("horario"), rs.getString("horario_fim"), rs.getBoolean("cancelado"), rs.getString("motivo_cancelamento"), rs.getString("data_fim"), OpeningHours.decode(rs.getString("funcionamento")), RoleCharacteristics.decode(rs.getString("caracteristicas")));
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
        String time = Inputs.optional(v.horario(), 5);
        if (time != null && (date == null || !time.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))) throw new IllegalArgumentException("Informe data e horário válidos para o evento.");
        String end = Inputs.optional(v.horarioFim(), 5);
        String endDate = Inputs.optional(v.dataFim(), 10);
        if (endDate != null) {
            try {
                if (!endDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || date == null || time == null || end == null || LocalDate.parse(endDate).isBefore(LocalDate.parse(date)))
                    throw new IllegalArgumentException("Informe início e término completos, com datas em ordem.");
            } catch (DateTimeParseException e) { throw new IllegalArgumentException("Data de término inválida."); }
        }
        if (end != null && (time == null || !end.matches("([01][0-9]|2[0-3]):[0-5][0-9]") || ((endDate == null || endDate.equals(date)) && end.compareTo(time) <= 0)))
            throw new IllegalArgumentException("O término deve ser posterior ao início. Para atravessar a meia-noite, informe a data de término.");
        return new Input(Inputs.required(v.nome(), 200), Inputs.optional(v.descricao(), 4000),
                Inputs.required(v.categoria(), 100), date, money, v.versao(), time, end, v.cancelado(), v.cancelado() ? Inputs.required(v.motivoCancelamento(), 2000) : null, endDate, OpeningHours.validate(v.funcionamento()), RoleCharacteristics.validate(v.caracteristicas()));
    }
    private ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Cadastro não encontrado."); }
    private ApiException conflict(String message) { return new ApiException(409, "ESTADO_INVALIDO", message); }
}
