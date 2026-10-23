package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Private plans with historical snapshots; no reservation or availability guarantee. */
public class ItineraryService {
    public record StopInput(Integer roleId, Integer minutos, String horario) { public StopInput(Integer roleId, Integer minutos) { this(roleId, minutos, null); } }
    public record Input(String nome, String cidade, String data, String partida, Integer duracaoMinutos,
                        Integer pessoas, Long orcamentoCentavos, String modoOrcamento, List<StopInput> paradas) {}
    public record Stop(int roleId, String nome, String local, int minutos, long precoCentavos, boolean concluida, String horario, boolean cancelado) {
        public Stop(int roleId, String nome, String local, int minutos, long precoCentavos, boolean concluida, String horario) {
            this(roleId, nome, local, minutos, precoCentavos, concluida, horario, false);
        }
    }
    public record Progress(Boolean concluida) {}
    public record Rename(String nome) {}
    public record Summary(int id, String nome, String cidade, String data, String partida, int pessoas,
                          int duracaoMinutos, long orcamentoCentavos, String modoOrcamento,
                          long totalCentavos, String criadoEm, int totalParadas, int paradasConcluidas) {}
    public record Detail(Summary roteiro, List<Stop> paradas) {}
    private final SqlAccess sql;
    private static final String SUMMARY_QUERY = """
            SELECT roteiros.*,
              (SELECT COUNT(*) FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id) AS total_paradas,
              (SELECT COUNT(*) FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id AND p.concluida=TRUE) AS paradas_concluidas
            FROM roteiros
            """;
    public ItineraryService() { this(new SqlAccess()); }
    public ItineraryService(SqlAccess sql) { this.sql = sql; }

    public Detail create(int account, Input input) {
        String city = Inputs.required(input.cidade(), 150);
        String name = Inputs.optional(input.nome(), 200);
        if (name == null) name = "Rolê em " + city;
        LocalDate date;
        try { date = LocalDate.parse(Inputs.required(input.data(), 10)); }
        catch (java.time.DateTimeException e) { throw new IllegalArgumentException("Invalid planned date."); }
        if (date.getYear() < 2000 || date.getYear() > 2100
                || input.partida() == null || !input.partida().matches("([01][0-9]|2[0-3]):[0-5][0-9]")) invalid();
        if (input.pessoas() == null || input.pessoas() < 1 || input.pessoas() > 50
                || input.duracaoMinutos() == null || input.duracaoMinutos() < 60 || input.duracaoMinutos() > 720
                || input.orcamentoCentavos() == null || input.orcamentoCentavos() < 0 || input.orcamentoCentavos() > 999999999L
                || !Set.of("person", "group").contains(input.modoOrcamento() == null ? "" : input.modoOrcamento())
                || input.paradas() == null || input.paradas().isEmpty() || input.paradas().size() > 48) invalid();
        Set<Integer> ids = new HashSet<>();
        int duration = 0;
        for (StopInput stop : input.paradas()) {
            if (stop == null || stop.roleId() == null || stop.roleId() < 1 || !ids.add(stop.roleId())
                    || stop.minutos() == null || stop.minutos() < 15 || stop.minutos() > 720 || stop.minutos() % 15 != 0) invalid();
            if (stop.horario() != null && !stop.horario().matches("([01][0-9]|2[0-3]):[0-5][0-9]")) invalid();
            duration += stop.minutos();
        }
        if (duration > input.duracaoMinutos()) throw new ApiException(400, "DURACAO_EXCEDIDA", "As paradas ultrapassam a duração do roteiro.");
        long budget = input.orcamentoCentavos() * (input.modoOrcamento().equals("person") ? input.pessoas() : 1);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                // Serialize saves for this account, bounding storage without a cross-account lock.
                try (var s = c.prepareStatement("SELECT id FROM contas WHERE id=? FOR UPDATE")) {
                    s.setInt(1, account); try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); }
                }
                try (var s = c.prepareStatement("SELECT COUNT(*) FROM roteiros WHERE conta_id=?")) {
                    s.setInt(1, account); try (var rs = s.executeQuery()) { rs.next(); if (rs.getInt(1) >= 100) throw new ApiException(409, "LIMITE_ROTEIROS", "Limite de 100 roteiros salvos nesta conta."); }
                }
                List<Stop> stops = new ArrayList<>();
                var snapshots = new java.util.HashMap<Integer, String>();
                var openingHours = new java.util.HashMap<Integer, List<OpeningHours.Rule>>();
                var eventEnds = new java.util.HashMap<Integer, Long>();
                long total = 0;
                String query = """
                        SELECT r.* FROM roles r WHERE r.id=? AND r.cancelado=FALSE AND
                        (r.local_id IS NULL OR EXISTS (
                            SELECT 1 FROM locais l JOIN organizacoes o ON o.id=l.organizacao_id
                            WHERE l.id=r.local_id AND l.status='PUBLICADO' AND o.status='APROVADA'))
                        """;
                for (StopInput stop : input.paradas()) {
                    try (var s = c.prepareStatement(query)) {
                        s.setInt(1, stop.roleId());
                        try (var rs = s.executeQuery()) {
                            if (!rs.next()) throw unavailable();
                            snapshots.put(stop.roleId(), SavedScheduleCheck.snapshot(rs));
                            openingHours.put(stop.roleId(), OpeningHours.decode(rs.getString("funcionamento")));
                            Date roleDate = rs.getDate("data");
                            Date endDate = rs.getDate("data_fim");
                            if (!city.equalsIgnoreCase(rs.getString("cidade")) || (roleDate != null && (date.isBefore(roleDate.toLocalDate()) || date.isAfter(endDate == null ? roleDate.toLocalDate() : endDate.toLocalDate())))) throw unavailable();
                            BigDecimal value = rs.getBigDecimal("valor");
                            if (value == null || value.signum() < 0) throw unavailable();
                            long cents = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
                            total = Math.addExact(total, Math.multiplyExact(cents, input.pessoas()));
                            String eventTime = rs.getString("horario");
                            if (roleDate != null && date.isAfter(roleDate.toLocalDate())) eventTime = null;
                            String endClock = rs.getString("horario_fim");
                            if (endClock != null) eventEnds.put(stop.roleId(), (endDate == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(date, endDate.toLocalDate()) * 1440) + java.time.LocalTime.parse(endClock).toSecondOfDay() / 60);
                            String time = stop.horario() == null ? eventTime : eventTime == null || stop.horario().compareTo(eventTime) < 0 ? stop.horario() : eventTime;
                            stops.add(new Stop(stop.roleId(), rs.getString("nome"), rs.getString("local"), stop.minutos(), cents, false, time));
                        }
                    }
                }
                int cursor = java.time.LocalTime.parse(input.partida()).toSecondOfDay() / 60;
                int departure = cursor;
                for (Stop stop : stops) {
                    if (stop.horario() != null) {
                        int fixed = java.time.LocalTime.parse(stop.horario()).toSecondOfDay() / 60;
                        if (cursor > fixed) throw new ApiException(400, "HORARIO_INCOMPATIVEL", "A ordem das paradas conflita com um horário marcado. Revise o roteiro.");
                        cursor = fixed;
                    }
                    if (!OpeningHours.fits(openingHours.get(stop.roleId()), date, cursor, cursor + stop.minutos()))
                        throw new ApiException(400, "FUNCIONAMENTO_INCOMPATIVEL", "A visita a " + stop.nome() + " fica fora dos horários de funcionamento informados. Revise o horário ou a duração.");
                    cursor += stop.minutos();
                    Long end = eventEnds.get(stop.roleId());
                    if (end != null && cursor > end)
                        throw new ApiException(400, "TERMINO_INCOMPATIVEL", "A visita a " + stop.nome() + " ultrapassa o término informado do evento. Reduza a duração ou revise as paradas.");
                }
                if (cursor - departure > input.duracaoMinutos())
                    throw new ApiException(400, "DURACAO_EXCEDIDA", "As paradas e a espera pelos horários marcados ultrapassam a duração do roteiro.");
                if (total > budget) throw new ApiException(409, "ORCAMENTO_EXCEDIDO", "O preço atual das paradas ultrapassa o orçamento. Atualize as opções.");
                int id;
                try (var s = c.prepareStatement("INSERT INTO roteiros (conta_id,nome,cidade,data_planejada,partida,duracao_minutos,pessoas,orcamento_centavos,modo_orcamento,total_centavos) VALUES (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    s.setInt(1, account); s.setString(2, name); s.setString(3, city); s.setDate(4, Date.valueOf(date));
                    s.setString(5, input.partida()); s.setInt(6, input.duracaoMinutos()); s.setInt(7, input.pessoas());
                    s.setLong(8, budget); s.setString(9, input.modoOrcamento()); s.setLong(10, total); s.executeUpdate();
                    try (var rs = s.getGeneratedKeys()) { if (!rs.next()) throw new SQLException("Missing itinerary key."); id = rs.getInt(1); }
                }
                try (var s = c.prepareStatement("INSERT INTO roteiro_paradas (roteiro_id,ordem,role_id,nome,local_nome,minutos,preco_centavos,horario,horarios_originais) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    for (int i = 0; i < stops.size(); i++) {
                        Stop stop = stops.get(i); s.setInt(1, id); s.setInt(2, i); s.setInt(3, stop.roleId()); s.setString(4, stop.nome());
                        s.setString(5, stop.local()); s.setInt(6, stop.minutos()); s.setLong(7, stop.precoCentavos()); s.setString(8, stop.horario()); s.setString(9, snapshots.get(stop.roleId())); s.addBatch();
                    }
                    s.executeBatch();
                }
                Detail saved = find(c, account, id);
                c.commit();
                return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Itinerary save failed.", e); }
    }

    public SavedScheduleCheck.Result check(int account, int id) {
        try (var c=sql.connection()) {
            c.setAutoCommit(false);
            var result=SavedScheduleCheck.check(c,account,id); c.commit(); return result;
        } catch(SQLException e) { throw new RuntimeException("Schedule check failed",e); }
    }
    public record RouteAlerts(int id, String nome, SavedScheduleCheck.Result verificacao) {}
    public List<RouteAlerts> alerts(int account) {
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            var ids=new ArrayList<Integer>(); var names=new ArrayList<String>();
            try(var s=c.prepareStatement("SELECT id,nome FROM roteiros WHERE conta_id=? AND EXISTS(SELECT 1 FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id AND p.concluida=FALSE) ORDER BY data_planejada,id LIMIT 100")) {
                s.setInt(1,account); try(var rs=s.executeQuery()) { while(rs.next()) { ids.add(rs.getInt(1)); names.add(rs.getString(2)); } }
            }
            var result=new ArrayList<RouteAlerts>();
            for(int i=0;i<ids.size();i++) result.add(new RouteAlerts(ids.get(i),names.get(i),SavedScheduleCheck.check(c,account,ids.get(i))));
            c.commit(); return result;
        } catch(SQLException e) { throw new RuntimeException("Schedule alerts failed",e); }
    }
    public List<Summary> list(int account, int offset) {
        if (offset < 0 || offset > 100) invalid();
        try (var c = sql.connection(); var s = c.prepareStatement(SUMMARY_QUERY + " WHERE conta_id=? ORDER BY id DESC LIMIT 20 OFFSET ?")) {
            s.setInt(1, account); s.setInt(2, offset); List<Summary> items = new ArrayList<>();
            try (var rs = s.executeQuery()) { while (rs.next()) items.add(summary(rs)); }
            return items;
        } catch (SQLException e) { throw new RuntimeException("Itinerary list failed.", e); }
    }
    public Detail rename(int account, int id, Rename input) {
        String name = Inputs.required(input == null ? null : input.nome(), 200);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                try (var s = c.prepareStatement("SELECT id FROM roteiros WHERE conta_id=? AND id=? FOR UPDATE")) {
                    s.setInt(1, account); s.setInt(2, id);
                    try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); }
                }
                try (var s = c.prepareStatement("UPDATE roteiros SET nome=? WHERE conta_id=? AND id=?")) {
                    s.setString(1, name); s.setInt(2, account); s.setInt(3, id); s.executeUpdate();
                }
                var result = find(c, account, id);
                c.commit(); return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Itinerary rename failed.", e); }
    }
    public void delete(int account, int id) {
        // The foreign key removes only this plan's snapshots in the same statement.
        try (var c = sql.connection(); var s = c.prepareStatement("DELETE FROM roteiros WHERE conta_id=? AND id=?")) {
            s.setInt(1, account); s.setInt(2, id);
            if (s.executeUpdate() != 1) throw missing();
        } catch (SQLException e) { throw new RuntimeException("Itinerary deletion failed.", e); }
    }
    public Detail find(int account, int id) {
        try (var c = sql.connection()) { return find(c, account, id); }
        catch (SQLException e) { throw new RuntimeException("Itinerary lookup failed.", e); }
    }
    public Detail progress(int account, int id, int roleId, Progress input) {
        if (input == null || input.concluida() == null) invalid();
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                try (var s = c.prepareStatement("SELECT id FROM roteiros WHERE conta_id=? AND id=? FOR UPDATE")) {
                    s.setInt(1, account); s.setInt(2, id);
                    try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); }
                }
                // Check existence separately: an idempotent update may affect zero rows.
                try (var s = c.prepareStatement("SELECT role_id FROM roteiro_paradas WHERE roteiro_id=? AND role_id=?")) {
                    s.setInt(1, id); s.setInt(2, roleId);
                    try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); }
                }
                try (var s = c.prepareStatement("UPDATE roteiro_paradas SET concluida=? WHERE roteiro_id=? AND role_id=?")) {
                    s.setBoolean(1, input.concluida()); s.setInt(2, id); s.setInt(3, roleId); s.executeUpdate();
                }
                var result = find(c, account, id);
                c.commit(); return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Itinerary progress failed.", e); }
    }
    private Detail find(Connection c, int account, int id) throws SQLException {
        try (var s = c.prepareStatement(SUMMARY_QUERY + " WHERE conta_id=? AND id=?")) {
            s.setInt(1, account); s.setInt(2, id); Summary header;
            try (var rs = s.executeQuery()) { if (!rs.next()) throw missing(); header = summary(rs); }
            List<Stop> stops = new ArrayList<>();
            try (var p = c.prepareStatement("SELECT p.*, COALESCE(r.cancelado,FALSE) AS cancelado FROM roteiro_paradas p LEFT JOIN roles r ON r.id=p.role_id WHERE p.roteiro_id=? ORDER BY p.ordem")) {
                p.setInt(1, id); try (var rs = p.executeQuery()) { while (rs.next()) stops.add(new Stop(rs.getInt("role_id"), rs.getString("nome"), rs.getString("local_nome"), rs.getInt("minutos"), rs.getLong("preco_centavos"), rs.getBoolean("concluida"), rs.getString("horario"), rs.getBoolean("cancelado"))); }
            }
            return new Detail(header, stops);
        } catch (SQLException e) { throw new RuntimeException("Itinerary lookup failed.", e); }
    }
    private Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getInt("id"), rs.getString("nome"), rs.getString("cidade"), rs.getDate("data_planejada").toString(), rs.getString("partida"), rs.getInt("pessoas"), rs.getInt("duracao_minutos"), rs.getLong("orcamento_centavos"), rs.getString("modo_orcamento"), rs.getLong("total_centavos"), rs.getTimestamp("criado_em").toInstant().toString(), rs.getInt("total_paradas"), rs.getInt("paradas_concluidas"));
    }
    private void invalid() { throw new IllegalArgumentException("Confira os campos do roteiro."); }
    private ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Roteiro não encontrado."); }
    private ApiException unavailable() { return new ApiException(409, "PARADA_INDISPONIVEL", "Uma parada mudou ou não está disponível para essa cidade e data. Atualize as opções."); }
}
