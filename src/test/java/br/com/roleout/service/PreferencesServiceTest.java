package br.com.roleout.service;

import br.com.roleout.dao.SqlAccess;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PreferencesServiceTest {
    private PreferencesService service;
    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection()) {
            for (String file : List.of("V001__roles.sql", "V002__organizacoes_e_locais.sql", "V009__horario_evento.sql", "V012__termino_evento.sql", "V014__cancelamento_publicado.sql", "V016__data_termino_evento.sql", "V018__funcionamento_evento.sql", "V023__caracteristicas_evento.sql", "V005__preferencias.sql")) {
                try (var r = new InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + file)), StandardCharsets.UTF_8)) { RunScript.execute(c, r); }
            }
            try (var s = c.createStatement()) { s.executeUpdate("INSERT INTO contas (nome,email,senha_hash) VALUES ('A','a@example.com','x'),('B','b@example.com','x')"); }
        }
        service = new PreferencesService(new SqlAccess(source));
    }
    private PreferencesService.Input input(boolean skip, List<String> music) {
        return new PreferencesService.Input(skip, music, List.of(), List.of("Museus"), List.of("Tranquilo"));
    }
    @Test void persistsAndReplacesOnlyOwnPreferences() {
        assertEquals("PENDENTE", service.get(1).perfil().status());
        var saved = service.save(1, input(false, List.of("Rock")));
        assertEquals(saved, service.get(1));
        assertEquals("PENDENTE", service.get(2).perfil().status());
        service.save(1, input(false, List.of("Pop")));
        assertEquals(List.of("Pop"), service.get(1).perfil().musica());
    }
    @Test void skippingAndClearingArePersisted() {
        service.save(1, input(true, List.of("Rock")));
        assertEquals("PULADO", service.get(1).perfil().status());
        assertTrue(service.get(1).perfil().musica().isEmpty());
        var empty = new PreferencesService.Input(false, List.of(), List.of(), List.of(), List.of());
        service.save(1, empty); service.save(1, empty);
        assertEquals("CONCLUIDO", service.get(1).perfil().status());
    }
    @Test void invalidSelectionsDoNotOverwriteExistingChoices() {
        var before = service.save(1, input(false, List.of("Rock")));
        assertThrows(IllegalArgumentException.class, () -> service.save(1, input(false, List.of("invalid"))));
        assertThrows(IllegalArgumentException.class, () -> service.save(1, input(false, List.of("Rock", "Rock"))));
        assertThrows(IllegalArgumentException.class, () -> service.save(1, input(false, null)));
        assertEquals(before, service.get(1));
    }
}
