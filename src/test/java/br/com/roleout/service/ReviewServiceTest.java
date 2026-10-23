package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.RoleDAO;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReviewServiceTest {
    JdbcDataSource source; OrganizationService orgs; ReviewService reviews; int org; int venue;
    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection()) {
            for (String file : List.of("V001__roles.sql", "V002__organizacoes_e_locais.sql", "V009__horario_evento.sql", "V012__termino_evento.sql", "V014__cancelamento_publicado.sql", "V016__data_termino_evento.sql", "V018__funcionamento_evento.sql", "V023__caracteristicas_evento.sql", "V003__sessoes.sql")) {
                try (var r = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + file)), StandardCharsets.UTF_8)) { RunScript.execute(c, r); }
            }
            try (var s = c.createStatement()) { s.executeUpdate("INSERT INTO contas (nome,email,senha_hash,administrador) VALUES ('Owner','o@example.com','x',false),('Admin','a@example.com','x',true),('Other','b@example.com','x',false)"); }
        }
        orgs = new OrganizationService(new SqlAccess(source)); reviews = new ReviewService(new SqlAccess(source));
        org = orgs.create(1, new OrganizationService.OrganizationInput("Museu", "MUSEU", null, "o@example.com", null)).id();
        venue = orgs.saveVenue(1, org, null, new OrganizationService.VenueInput("Museu", "Cultura", "Rua 1", "Uberaba", "MG", null, null, null)).id();
    }
    ReviewService.Item queued(Integer local) { return reviews.pending(2, 0).stream().filter(v -> Objects.equals(v.localId(), local)).findFirst().orElseThrow(); }
    ReviewService.Decision decision(ReviewService.Item item, String choice) { return new ReviewService.Decision(item.organizacaoId(), item.localId(), item.versao(), choice, "Conferido no teste"); }
    @Test void publicationRequiresBothApprovalsAndWritesHistory() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate("INSERT INTO roles(nome,cidade,valor,local_id) VALUES ('Teste','Uberaba',0," + venue + ")"); }
        var dao = new RoleDAO(source); assertNull(dao.buscarPublicadoPorId(1));
        orgs.submitVenue(1, org, venue);
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, decision(queued(venue), "APROVAR"))).status());
        assertTrue(orgs.reviews(1, org).isEmpty());
        reviews.decide(2, decision(queued(null), "APROVAR"));
        assertNull(dao.buscarPublicadoPorId(1));
        reviews.decide(2, decision(queued(venue), "APROVAR"));
        assertNotNull(dao.buscarPublicadoPorId(1)); assertEquals(2, orgs.reviews(1, org).size());
    }
    @Test void forbidsNonAdminsSelfReviewAndOtherOwners() throws Exception {
        assertEquals(403, assertThrows(ApiException.class, () -> reviews.pending(1, 0)).status());
        var choice = decision(queued(null), "APROVAR");
        assertEquals(403, assertThrows(ApiException.class, () -> reviews.decide(1, choice)).status());
        try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate("UPDATE contas SET administrador=true WHERE id=1"); }
        assertEquals(403, assertThrows(ApiException.class, () -> reviews.decide(1, choice)).status());
        assertEquals(404, assertThrows(ApiException.class, () -> orgs.submitVenue(3, org, venue)).status());
        assertEquals(404, assertThrows(ApiException.class, () -> orgs.reviews(3, org)).status());
    }
    @Test void staleAndRepeatedDecisionsAreRejected() {
        var stale = decision(queued(null), "APROVAR");
        orgs.update(1, org, new OrganizationService.OrganizationInput("Museu alterado", "MUSEU", null, "o@example.com", null));
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, stale)).status());
        var current = decision(queued(null), "REJEITAR"); reviews.decide(2, current);
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, current)).status());
        assertEquals(1, orgs.reviews(1, org).size());
    }
    @Test void rejectionCanBeCorrectedAndResubmitted() {
        orgs.submitVenue(1, org, venue); reviews.decide(2, decision(queued(venue), "REJEITAR"));
        assertEquals("REJEITADO", orgs.findVenue(1, org, venue).status());
        orgs.saveVenue(1, org, venue, new OrganizationService.VenueInput("Corrigido", "Cultura", "Rua 2", "Uberaba", "MG", null, null, null));
        assertEquals("PENDENTE", orgs.submitVenue(1, org, venue).status());
        assertEquals(409, assertThrows(ApiException.class, () -> orgs.submitVenue(1, org, venue)).status());
    }
    @Test void simultaneousReviewsRecordOnlyOneDecision() throws Exception {
        var input = decision(queued(null), "APROVAR");
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> action = () -> { start.await(); try { reviews.decide(2, input); return 200; } catch (ApiException e) { return e.status(); } };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertEquals(Set.of(200, 409), Set.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)));
            assertEquals(1, orgs.reviews(1, org).size());
        } finally { executor.shutdownNow(); }
    }
}
