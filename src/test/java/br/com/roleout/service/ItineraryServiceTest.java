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
            for (String file : new String[]{"V001__roles.sql", "V002__organizacoes_e_locais.sql", "V009__horario_evento.sql", "V012__termino_evento.sql", "V014__cancelamento_publicado.sql", "V016__data_termino_evento.sql", "V018__funcionamento_evento.sql", "V023__caracteristicas_evento.sql", "V003__sessoes.sql", "V004__roteiros.sql", "V020__horarios_salvos.sql", "V008__progresso_roteiro.sql", "V011__horario_parada.sql"}) {
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
    @Test void detectsPublishedChangesWithoutRewritingOriginalPlans() throws Exception {
        execute("UPDATE roles SET horario='11:00',horario_fim='13:00',data='2026-10-15' WHERE id=1");
        var saved=service.create(1,input(10000,"group",List.of(new ItineraryService.StopInput(1,60))));
        int id=saved.roteiro().id();
        assertTrue(service.check(1,id).alertas().isEmpty());
        execute("UPDATE roles SET horario_fim='14:00' WHERE id=1");
        var alerts=service.check(1,id).alertas();
        assertEquals(List.of("HORARIOS_ALTERADOS"),alerts.stream().map(SavedScheduleCheck.Alert::codigo).toList());
        execute("UPDATE roles SET horario_fim='11:30' WHERE id=1");
        alerts=service.check(1,id).alertas();
        assertTrue(alerts.stream().anyMatch(a->a.codigo().equals("HORARIO_INCOMPATIVEL")));
        assertEquals(saved,service.find(1,id));
        assertEquals(1,service.alerts(1).size()); assertTrue(service.alerts(2).isEmpty());
        assertEquals(404,assertThrows(ApiException.class,()->service.check(2,id)).status());
        assertEquals(404,assertThrows(ApiException.class,()->service.check(1,99999)).status());
        service.progress(1,id,1,new ItineraryService.Progress(true));
        assertTrue(service.check(1,id).alertas().isEmpty()); assertTrue(service.alerts(1).isEmpty());
    }
    @Test void legacySchedulesAreUnknownAndCurrentClosuresAreStillChecked() throws Exception {
        var saved=service.create(1,input(10000,"group",List.of(new ItineraryService.StopInput(1,60))));
        int id=saved.roteiro().id(); execute("UPDATE roteiro_paradas SET horarios_originais=NULL WHERE roteiro_id="+id);
        var rules=List.of(new OpeningHours.Rule("2026-10-15",List.<OpeningHours.Window>of()));
        execute("UPDATE roles SET funcionamento='"+OpeningHours.encode(rules)+"' WHERE id=1");
        var codes=service.check(1,id).alertas().stream().map(SavedScheduleCheck.Alert::codigo).toList();
        assertTrue(codes.contains("SEM_HISTORICO")); assertTrue(codes.contains("HORARIO_INCOMPATIVEL")); assertFalse(codes.contains("HORARIOS_ALTERADOS"));
        assertEquals(saved,service.find(1,id));
        execute("UPDATE roles SET cancelado=TRUE WHERE id=1");
        assertEquals("CANCELADO",service.check(1,id).alertas().get(0).codigo());
        execute("DELETE FROM roles WHERE id=1");
        assertEquals("INDISPONIVEL",service.check(1,id).alertas().get(0).codigo());
    }
    @Test void detectsWeeklyChangesAndKeepsAlertsAcrossRepeatedChecks() throws Exception {
        var hours=List.of(new OpeningHours.Rule("4",List.of(new OpeningHours.Window("09:00","18:00"))));
        execute("UPDATE roles SET funcionamento='"+OpeningHours.encode(hours)+"' WHERE id=1");
        int id=service.create(1,input(10000,"group",List.of(new ItineraryService.StopInput(1,60)))).roteiro().id();
        assertTrue(service.check(1,id).alertas().isEmpty());
        execute("UPDATE roles SET funcionamento=NULL WHERE id=1");
        assertEquals("HORARIOS_ALTERADOS",service.check(1,id).alertas().get(0).codigo());
        assertEquals(service.check(1,id).alertas(),service.check(1,id).alertas());
    }
    @Test void rechecksApprovedOpeningHoursAndKeepsHistoricalPlans() throws Exception {
        var hours = List.of(new OpeningHours.Rule("4",List.of(new OpeningHours.Window("09:00","11:00"))));
        execute("UPDATE roles SET funcionamento='" + OpeningHours.encode(hours) + "' WHERE id=1");
        var saved=service.create(1,input(10000,"group",List.of(new ItineraryService.StopInput(1,60))));
        assertEquals("FUNCIONAMENTO_INCOMPATIVEL", assertThrows(ApiException.class,()->service.create(1,input(10000,"group",List.of(new ItineraryService.StopInput(1,75))))).code());
        var closed=List.of(new OpeningHours.Rule("2026-10-15",List.<OpeningHours.Window>of()));
        execute("UPDATE roles SET funcionamento='" + OpeningHours.encode(closed) + "' WHERE id=1");
        assertThrows(ApiException.class,()->service.create(1,input(10000,"group",List.of(new ItineraryService.StopInput(1,60)))));
        assertEquals(saved,service.find(1,saved.roteiro().id()));
        assertEquals(1,service.list(1,0).size());
    }
    @Test void overnightAndLaterDayVisitsRespectActualEndAndPreserveSnapshots() throws Exception {
        execute("UPDATE roles SET data='2027-12-31',data_fim='2028-01-02',horario='22:00',horario_fim='02:00' WHERE id=1");
        var overnight = service.create(1, new ItineraryService.Input("Virada", "Uberaba", "2027-12-31", "22:00", 240, 1, 10000L, "group", List.of(new ItineraryService.StopInput(1, 240))));
        assertEquals("22:00", overnight.paradas().get(0).horario());
        var finalDay = new ItineraryService.Input("Último dia", "Uberaba", "2028-01-02", "01:00", 60, 1, 10000L, "group", List.of(new ItineraryService.StopInput(1, 60)));
        var saved = service.create(1, finalDay);
        assertNull(saved.paradas().get(0).horario());
        assertThrows(ApiException.class, () -> service.create(1, new ItineraryService.Input("Tarde", "Uberaba", "2028-01-02", "01:00", 90, 1, 10000L, "group", List.of(new ItineraryService.StopInput(1, 75)))));
        assertThrows(ApiException.class, () -> service.create(1, new ItineraryService.Input("Depois", "Uberaba", "2028-01-03", "01:00", 60, 1, 10000L, "group", List.of(new ItineraryService.StopInput(1, 60)))));
        execute("UPDATE roles SET horario_fim='01:30' WHERE id=1");
        assertThrows(ApiException.class, () -> service.create(1, finalDay));
        assertEquals(saved, service.find(1, saved.roteiro().id()));
        assertEquals(2, service.list(1, 0).size());
    }
    @Test void preservesVisitTimesAndPublishedEventLimits() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate("UPDATE roles SET horario='11:00' WHERE id=1"); }
        var created = service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(2, 30, "10:15"), new ItineraryService.StopInput(1, 60, "12:00"))));
        assertEquals("10:15", service.find(1, created.roteiro().id()).paradas().get(0).horario());
        assertEquals("11:00", created.paradas().get(1).horario());
        assertThrows(ApiException.class, () -> service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(2, 90), new ItineraryService.StopInput(1, 60)))));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(2, 30, "99:00")))));
    }
    @Test void rechecksEndTimeAndRejectsWithoutWritingPartialPlans() throws Exception {
        execute("UPDATE roles SET data='2026-10-15',horario='11:00',horario_fim='12:00' WHERE id=1");
        var exact = service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(1, 60))));
        assertEquals(1, exact.paradas().size());
        assertEquals("TERMINO_INCOMPATIVEL", assertThrows(ApiException.class, () -> service.create(1,
            input(10000, "group", List.of(new ItineraryService.StopInput(1, 75))))).code());
        execute("UPDATE roles SET horario_fim='11:30' WHERE id=1");
        assertThrows(ApiException.class, () -> service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(1, 60)))));
        assertEquals(1, service.list(1, 0).size());
        assertEquals(exact, service.find(1, exact.roteiro().id()));
    }
    @Test void includesWaitingInDurationAndAllowsExactBoundary() {
        assertEquals("DURACAO_EXCEDIDA", assertThrows(ApiException.class, () -> service.create(1,
            input(10000, "group", List.of(new ItineraryService.StopInput(1, 60, "14:00"))))).code());
        assertTrue(service.list(1, 0).isEmpty());
        assertEquals(1, service.create(1, input(10000, "group", List.of(new ItineraryService.StopInput(1, 60, "13:00")))).paradas().size());
    }

    @Test void cancelledEventsCannotEnterNewPlansAndOldSnapshotsSurvive() throws Exception {
        var saved = service.create(1, input(10000, "group", stops()));
        execute("UPDATE roles SET cancelado=TRUE,motivo_cancelamento='Manutenção',nome='Nome novo' WHERE id=1");
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, input(10000, "group", stops()))).status());
        var current = service.find(1, saved.roteiro().id());
        assertEquals(saved.roteiro(), current.roteiro());
        assertEquals(saved.paradas().get(1).nome(), current.paradas().get(1).nome());
        assertEquals(saved.paradas().get(1).precoCentavos(), current.paradas().get(1).precoCentavos());
        assertTrue(current.paradas().get(1).cancelado());
        assertFalse(current.paradas().get(0).cancelado());
        assertEquals(1, service.list(1, 0).size());
    }
    private void execute(String query) throws Exception { try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate(query); } }
    @Test void progressIsPrivatePersistentIdempotentAndReversible() {
        var original = service.create(1, input(3000, "person", stops()));
        int id = original.roteiro().id();
        assertFalse(original.paradas().get(0).concluida());
        assertEquals(404, assertThrows(ApiException.class, () -> service.progress(2, id, 2, new ItineraryService.Progress(true))).status());
        assertEquals(404, assertThrows(ApiException.class, () -> service.progress(1, id, 999, new ItineraryService.Progress(true))).status());
        assertThrows(IllegalArgumentException.class, () -> service.progress(1, id, 2, new ItineraryService.Progress(null)));
        var completed = service.progress(1, id, 2, new ItineraryService.Progress(true));
        assertTrue(completed.paradas().get(0).concluida());
        assertFalse(completed.paradas().get(1).concluida());
        assertEquals(original.roteiro().totalCentavos(), completed.roteiro().totalCentavos());
        assertEquals(2, completed.roteiro().totalParadas());
        assertEquals(1, completed.roteiro().paradasConcluidas());
        assertEquals(completed.roteiro(), service.list(1, 0).get(0));
        assertTrue(service.list(2, 0).isEmpty());
        assertEquals(completed, service.find(1, id));
        assertEquals(completed, service.progress(1, id, 2, new ItineraryService.Progress(true)));
        assertEquals(original, service.progress(1, id, 2, new ItineraryService.Progress(false)));
    }
    @Test void renameIsPrivatePersistentAndPreservesTheWholePlan() {
        int id = service.create(1, input(3000, "person", stops())).roteiro().id();
        var original = service.progress(1, id, 2, new ItineraryService.Progress(true));
        assertEquals(404, assertThrows(ApiException.class, () -> service.rename(2, id, new ItineraryService.Rename("Outro"))).status());
        assertEquals(original, service.find(1, id));
        for (String name : new String[]{null, "  ", "a".repeat(201)}) {
            assertThrows(IllegalArgumentException.class, () -> service.rename(1, id, new ItineraryService.Rename(name)));
        }
        var renamed = service.rename(1, id, new ItineraryService.Rename("  Aniversário  "));
        assertEquals("Aniversário", renamed.roteiro().nome());
        assertEquals(original.paradas(), renamed.paradas());
        assertEquals(renamed, service.find(1, id));
        assertEquals(List.of(renamed.roteiro()), service.list(1, 0));
        assertEquals(renamed, service.rename(1, id, new ItineraryService.Rename("Aniversário")));
        assertEquals(original, service.rename(1, id, new ItineraryService.Rename(original.roteiro().nome())));
        assertEquals(200, service.rename(1, id, new ItineraryService.Rename("a".repeat(200))).roteiro().nome().length());
    }
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
