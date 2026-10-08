package br.com.roleout.service;

import br.com.roleout.dao.SqlAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.sql.SQLException;

public class PreferencesService {
    public record Input(Boolean pular, List<String> musica, List<String> comida, List<String> atividades, List<String> ambientes) {}
    public record Profile(String status, List<String> musica, List<String> comida, List<String> atividades, List<String> ambientes) {}
    public record View(Profile perfil, Map<String, List<String>> opcoes) {}
    private static final Map<String, List<String>> OPTIONS = Map.of(
        "musica", List.of("Pop", "Rock", "Sertanejo", "MPB", "Samba e pagode", "Eletrônica", "Forró", "Hip-hop"),
        "comida", List.of("Brasileira", "Italiana", "Japonesa", "Hambúrguer", "Vegetariana", "Cafés e doces"),
        "atividades", List.of("Música ao vivo", "Cinema", "Museus", "Gastronomia", "Passeios ao ar livre", "Esportes"),
        "ambientes", List.of("Tranquilo", "Animado", "Ao ar livre", "Familiar", "Romântico", "Cultural"));
    private final SqlAccess sql;
    private final ObjectMapper json = new ObjectMapper();
    public PreferencesService() { this(new SqlAccess()); }
    public PreferencesService(SqlAccess sql) { this.sql = sql; }
    private Profile empty(String status) { return new Profile(status, List.of(), List.of(), List.of(), List.of()); }
    public View get(int account) {
        try (var c = sql.connection(); var s = c.prepareStatement("SELECT conteudo FROM preferencias_conta WHERE conta_id=?")) {
            s.setInt(1, account);
            try (var rs = s.executeQuery()) {
                return new View(rs.next() ? json.readValue(rs.getString(1), Profile.class) : empty("PENDENTE"), OPTIONS);
            }
        } catch (SQLException | java.io.IOException e) { throw new RuntimeException("Preferences lookup failed.", e); }
    }
    private List<String> validate(String key, List<String> values) {
        if (values == null || values.size() > OPTIONS.get(key).size()
                || new HashSet<>(values).size() != values.size()
                || values.stream().anyMatch(v -> v == null || !OPTIONS.get(key).contains(v))) {
            throw new IllegalArgumentException("Invalid preference selection.");
        }
        return List.copyOf(values);
    }
    public View save(int account, Input input) {
        if (input.pular() == null) throw new IllegalArgumentException("Missing skip choice.");
        var profile = new Profile(input.pular() ? "PULADO" : "CONCLUIDO",
                validate("musica", input.musica()), validate("comida", input.comida()),
                validate("atividades", input.atividades()), validate("ambientes", input.ambientes()));
        if (input.pular()) profile = empty("PULADO");
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                // Serialize writes per account, including the initial insertion.
                try (var lock = c.prepareStatement("SELECT id FROM contas WHERE id=? FOR UPDATE")) {
                    lock.setInt(1, account);
                    try (var rs = lock.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Missing account."); }
                }
                String content = json.writeValueAsString(profile);
                try (var update = c.prepareStatement("UPDATE preferencias_conta SET conteudo=? WHERE conta_id=?")) {
                    update.setString(1, content); update.setInt(2, account);
                    if (update.executeUpdate() == 0) {
                        try (var insert = c.prepareStatement("INSERT INTO preferencias_conta (conta_id,conteudo) VALUES (?,?)")) {
                            insert.setInt(1, account); insert.setString(2, content); insert.executeUpdate();
                        }
                    }
                }
                c.commit();
                return new View(profile, OPTIONS);
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (SQLException | java.io.IOException e) { throw new RuntimeException("Preferences save failed.", e); }
    }
}
