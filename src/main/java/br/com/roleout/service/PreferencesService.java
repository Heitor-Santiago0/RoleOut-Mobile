package br.com.roleout.service;

import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.PreferencesDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.sql.SQLException;

public class PreferencesService {
    public record Input(Boolean pular, List<String> musica, List<String> comida, List<String> atividades, List<String> ambientes) {}
    public record Profile(String status, List<String> musica, List<String> comida, List<String> atividades, List<String> ambientes) {}
    public record View(Profile perfil, Map<String, List<String>> opcoes) {}
    static final Map<String, List<String>> OPTIONS = br.com.roleout.model.PreferenceOptions.OPTIONS;
    private final SqlAccess sql;
    private final ObjectMapper json = new ObjectMapper();
    public PreferencesService() { this(new SqlAccess()); }
    public PreferencesService(SqlAccess sql) { this.sql = sql; }
    private Profile empty(String status) { return new Profile(status, List.of(), List.of(), List.of(), List.of()); }
    public View get(int account) {
        try (var c = sql.connection()) {
            String content=new PreferencesDAO(c).content(account);
            return new View(content==null?empty("PENDENTE"):json.readValue(content,Profile.class),OPTIONS);
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
                var dao=new PreferencesDAO(c);
                if(!dao.lockAccount(account))throw new IllegalArgumentException("Missing account.");
                dao.save(account,json.writeValueAsString(profile));
                c.commit();
                return new View(profile, OPTIONS);
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (SQLException | java.io.IOException e) { throw new RuntimeException("Preferences save failed.", e); }
    }
}
