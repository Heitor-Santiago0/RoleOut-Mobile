package br.com.roleout.service;

import br.com.roleout.api.ApiException;
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

class ItineraryServiceTest {
    private JdbcDataSource source;
    private ItineraryService service;
    @BeforeEach void schema() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection()) {
            for (String file : new String[]{"V001__roles.sql", "V002__organizacoes_e_locais.sql", "V003__sessoes.sql", "V004__roteiros.sql"}) {
                try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + file)), StandardCharsets.UTF_8)) { RunScript.execute(c, reader); }
            }
            try (var s = c.createStatement()) {
                s.executeUpdate("INSERT INTO contas (nome,email,senha_hash) VALUES ('A','a@example.com','test'),('B','b@example.com','test')");
                s.executeUpdate("INSERT INTO roles (nome,cidade,local,valor,categoria) VALUES ('Museu','Uberaba','Centro',20.10,'Cultura'),('Parque','Uberaba','Parque',0,'Natureza')");
            }
        }
        service = new ItineraryService(new SqlAccess(source));
    }
    private ItineraryService.Input input(long budget, String mode, List<ItineraryService.StopInput> stops) {
        return new ItineraryService.Input("Meu passeio", "Uberaba", "2026-10-15", "10:00", 240, 3, budget, mode, stops);
    }
    private List<ItineraryService.StopInput> stops() { return List.of(new ItineraryService.StopInput(2, 30), new ItineraryService.StopInput(1, 60)); }
    private void execute(String query) throws Exception { try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate(query); } }
    @Test void savesOrderAndGroupBudgetAndPreventsCrossAccountReads() {
        var saved = service.create(1, input(3000, "person", stops()));
        assertEquals(9000, saved.roteiro().orcamentoCentavos());
        assertEquals(6030, saved.roteiro().totalCentavos());
        assertEquals(2, saved.paradas().get(0).roleId());
        assertEquals(2010, saved.paradas().get(1).precoCentavos());
        assertEquals(1, service.list(1, 0).size());
        assertTrue(service.list(2, 0).isEmpty());
        assertEquals(404, assertThrows(ApiException.class, () -> service.find(2, saved.roteiro().id())).status());
        assertEquals(saved, service.find(1, saved.roteiro().id()));
    }
    @Test void deletionIsPrivateCascadesSnapshotsAndPreservesOtherPlansAndCatalog() throws Exception {
        var first = service.create(1, input(3000, "person", stops()));
        var second = service.create(1, input(3000, "person", stops()));
        var other = service.create(2, input(3000, "person", stops()));
        int id = first.roteiro().id();
        assertEquals(404, assertThrows(ApiException.class, () -> service.delete(2, id)).status());
        assertEquals(first, service.find(1, id));
        service.delete(1, id);
        assertEquals(404, assertThrows(ApiException.class, () -> service.find(1, id)).status());
        assertEquals(404, assertThrows(ApiException.class, () -> service.delete(1, id)).status());
        assertEquals(List.of(second.roteiro()), service.list(1, 0));
        assertEquals(other, service.find(2, other.roteiro().id()));
        try (var c = source.getConnection(); var s = c.createStatement()) {
            try (var rs = s.executeQuery("SELECT COUNT(*) FROM roteiro_paradas WHERE roteiro_id=" + id)) { rs.next(); assertEquals(0, rs.getInt(1)); }
            try (var rs = s.executeQuery("SELECT COUNT(*) FROM roles")) { rs.next(); assertEquals(2, rs.getInt(1)); }
        }
    }
    @Test void saveUsesOnePooledConnectionWithoutNestedBorrowing() {
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setDataSource(source); config.setMaximumPoolSize(1); config.setMinimumIdle(0); config.setConnectionTimeout(1000);
        try (var pool = new com.zaxxer.hikari.HikariDataSource(config)) {
            var pooledService = new ItineraryService(new SqlAccess(pool));
            var saved = pooledService.create(1, input(7000, "group", stops()));
            assertEquals(6030, saved.roteiro().totalCentavos());
            assertEquals(1, pooledService.list(1, 0).size());
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
        }
    }
    @Test void pricesAreServerCalculatedAndFailedSaveLeavesNoPartialPlan() throws Exception {
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, input(6000, "group", stops()))).status());
        assertTrue(service.list(1, 0).isEmpty());
        execute("UPDATE roles SET valor=40 WHERE id=1");
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, input(3000, "person", stops()))).status());
        assertTrue(service.list(1, 0).isEmpty());
    }
    @Test void snapshotsSurviveLegacyRoleDeletionWithoutChangingSavedCosts() throws Exception {
        var saved = service.create(1, input(7000, "group", stops()));
        execute("DELETE FROM roles WHERE id=1");
        assertEquals(saved, service.find(1, saved.roteiro().id()));
    }
    @Test void validationAndPublicationRulesCannotBeBypassed() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.create(1, input(10000, "group", List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(1, 60), new ItineraryService.StopInput(1, 60)))));
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(1, 255))))).status());
        execute("UPDATE roles SET data='2026-10-16' WHERE id=1");
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, input(10000, "group", stops()))).status());
        execute("UPDATE roles SET data=NULL WHERE id=1");
        execute("INSERT INTO organizacoes (nome,tipo,contato_email) VALUES ('Museu','MUSEU','m@example.com')");
        execute("INSERT INTO locais (organizacao_id,nome,categoria,endereco,cidade,uf) VALUES (1,'Museu','Cultura','Rua','Uberaba','MG')");
        execute("UPDATE roles SET local_id=1 WHERE id=1");
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, input(10000, "group", stops()))).status());
        assertTrue(service.list(1, 0).isEmpty());
    }
}
