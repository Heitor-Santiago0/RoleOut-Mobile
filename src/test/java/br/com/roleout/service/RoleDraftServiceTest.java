package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.RoleDAO;
import br.com.roleout.dao.SqlAccess;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleDraftServiceTest {
    JdbcDataSource source; RoleDraftService drafts; OrganizationService orgs; int org; int venue;
    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection()) {
            for (var file : List.of("V001__roles.sql", "V002__organizacoes_e_locais.sql", "V006__rascunhos_roles.sql", "V007__revisao_roles.sql")) {
                try (var r = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + file)), StandardCharsets.UTF_8)) { RunScript.execute(c, r); }
            }
            try (var s = c.createStatement()) { s.executeUpdate("INSERT INTO contas(nome,email,senha_hash) VALUES ('Owner','owner@example.invalid','x'),('Other','other@example.invalid','x')"); }
        }
        drafts = new RoleDraftService(new SqlAccess(source)); orgs = new OrganizationService(new SqlAccess(source));
        org = orgs.create(1, new OrganizationService.OrganizationInput("Museu", "MUSEU", null, "owner@example.invalid", null)).id();
        venue = orgs.saveVenue(1, org, null, new OrganizationService.VenueInput("Museu", "Cultura", "Rua 1", "Uberaba", "MG", null, null, null)).id();
    }
    void approve() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("UPDATE organizacoes SET status='APROVADA'"); s.executeUpdate("UPDATE locais SET status='PUBLICADO'");
        }
    }
    RoleDraftService.Input input(String value, String date, Integer version) {
        return new RoleDraftService.Input("Visita", "Exposição", "Cultura", date, new BigDecimal(value), version);
    }
    @Test void requiresBothApprovalsAndMembership() throws Exception {
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, null, input("10", null, null))).status());
        try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate("UPDATE organizacoes SET status='APROVADA'"); }
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, null, input("10", null, null))).status());
        approve();
        assertEquals(404, assertThrows(ApiException.class, () -> drafts.save(2, venue, null, input("10", null, null))).status());
        assertEquals(404, assertThrows(ApiException.class, () -> drafts.list(2, venue, 0)).status());
    }
    @Test void storesMoneyAndOptionalDateWithoutPublishingOrChangingLegacyRecords() throws Exception {
        approve();
        try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO roles(nome,cidade,valor) VALUES ('Legado','Uberaba',5)"); }
        var created = drafts.save(1, venue, null, input("12.35", "2027-02-28", null));
        assertEquals(new BigDecimal("12.35"), created.valor()); assertEquals("2027-02-28", created.data());
        var updated = drafts.save(1, venue, created.id(), input("0", null, 1));
        assertEquals(2, updated.versao()); assertNull(updated.data());
        assertEquals(1, drafts.list(1, venue, 0).size());
        var publicItems = new RoleDAO(source).consultarPagina(null, null, null, 20, 0);
        assertEquals(1, publicItems.size()); assertEquals("Legado", publicItems.get(0).getNome());
        assertEquals(1, new RoleDAO(source).listarTodos().size());
    }
    @Test void rejectsInvalidMoneyDatesAndStaleEdits() throws Exception {
        approve();
        for (String value : List.of("-1", "1.001", "100000000")) assertThrows(IllegalArgumentException.class, () -> drafts.save(1, venue, null, input(value, null, null)));
        assertThrows(IllegalArgumentException.class, () -> drafts.save(1, venue, null, input("0", "2027-02-29", null)));
        var created = drafts.save(1, venue, null, input("1", null, null));
        drafts.save(1, venue, created.id(), input("2", null, 1));
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, created.id(), input("3", null, 1))).status());
        assertEquals(new BigDecimal("2.00"), drafts.list(1, venue, 0).get(0).valor());
    }
    @Test void preventsCrossVenueEditsAndWritesAfterSuspension() throws Exception {
        var second = orgs.saveVenue(1, org, null, new OrganizationService.VenueInput("Outra unidade", "Cultura", "Rua 2", "Uberaba", "MG", null, null, null)).id();
        approve(); var created = drafts.save(1, venue, null, input("1", null, null));
        assertEquals(404, assertThrows(ApiException.class, () -> drafts.save(1, second, created.id(), input("2", null, 1))).status());
        try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate("UPDATE locais SET status='SUSPENSO'"); }
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, created.id(), input("2", null, 1))).status());
        assertEquals(1, drafts.list(1, venue, 0).size());
    }
    @Test void concurrentEditsKeepOneWinner() throws Exception {
        approve(); var created = drafts.save(1, venue, null, input("1", null, null));
        var start = new java.util.concurrent.CountDownLatch(1); var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> action = () -> { start.await(); try { drafts.save(1, venue, created.id(), input("2", null, 1)); return 200; } catch (ApiException e) { return e.status(); } };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertEquals(Set.of(200, 409), Set.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)));
            assertEquals(2, drafts.list(1, venue, 0).get(0).versao());
        } finally { executor.shutdownNow(); }
    }
}
