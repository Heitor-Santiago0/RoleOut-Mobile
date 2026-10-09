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

class RoleReviewServiceTest {
    JdbcDataSource source; RoleDraftService drafts; RoleReviewService reviews; int venue; int id;
    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection()) {
            for (var file : List.of("V001__roles.sql", "V002__organizacoes_e_locais.sql", "V003__sessoes.sql", "V006__rascunhos_roles.sql", "V007__revisao_roles.sql")) {
                try (var r = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + file)), StandardCharsets.UTF_8)) { RunScript.execute(c, r); }
            }
            try (var s = c.createStatement()) {
                s.executeUpdate("INSERT INTO contas(nome,email,senha_hash,administrador) VALUES ('Owner','owner@example.invalid','x',false),('Admin','admin@example.invalid','x',true),('Other','other@example.invalid','x',false)");
            }
        }
        var orgs = new OrganizationService(new SqlAccess(source));
        int org = orgs.create(1, new OrganizationService.OrganizationInput("Museu", "MUSEU", null, "owner@example.invalid", null)).id();
        venue = orgs.saveVenue(1, org, null, new OrganizationService.VenueInput("Museu", "Cultura", "Rua 1", "Uberaba", "MG", null, null, null)).id();
        execute("UPDATE organizacoes SET status='APROVADA'"); execute("UPDATE locais SET status='PUBLICADO'");
        drafts = new RoleDraftService(new SqlAccess(source)); reviews = new RoleReviewService(new SqlAccess(source));
        id = drafts.save(1, venue, null, input(null)).id();
    }
    void execute(String query) throws Exception { try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate(query); } }
    RoleDraftService.Input input(Integer version) { return new RoleDraftService.Input("Visita", "Acervo", "Cultura", null, new BigDecimal("12.35"), version); }
    RoleReviewService.Decision choice(int version, String decision) { return new RoleReviewService.Decision(id, version, decision, "Conferido"); }
    @Test void publishesOnlyAfterReviewWithServerVenueAndPrice() {
        var dao = new RoleDAO(source);
        assertTrue(dao.consultarPagina(null, null, null, 20, 0).isEmpty());
        drafts.submit(1, venue, id, new RoleDraftService.Submission(1));
        assertTrue(dao.consultarPagina(null, null, null, 20, 0).isEmpty());
        var saved = reviews.decide(2, choice(2, "APROVAR"));
        assertEquals("PUBLICADO", saved.role().status());
        var published = dao.buscarPublicadoPorId(saved.role().roleId());
        assertEquals("Rua 1", published.getLocal()); assertEquals("Uberaba", published.getCidade()); assertEquals(12.35, published.getValor());
        assertEquals(1, drafts.reviews(1, venue, id).size());
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, id, input(3))).status());
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, choice(2, "APROVAR"))).status());
        assertEquals(1, dao.consultarPagina(null, null, null, 20, 0).size());
    }
    @Test void preventsOwnersOtherAccountsAndStaleSubmission() throws Exception {
        assertEquals(404, assertThrows(ApiException.class, () -> drafts.submit(3, venue, id, new RoleDraftService.Submission(1))).status());
        assertEquals(404, assertThrows(ApiException.class, () -> drafts.reviews(3, venue, id)).status());
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.submit(1, venue, id, new RoleDraftService.Submission(2))).status());
        drafts.submit(1, venue, id, new RoleDraftService.Submission(1));
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, id, input(2))).status());
        assertEquals(403, assertThrows(ApiException.class, () -> reviews.pending(1, 0)).status());
        assertEquals(403, assertThrows(ApiException.class, () -> reviews.decide(1, choice(2, "APROVAR"))).status());
        execute("UPDATE contas SET administrador=true WHERE id=1");
        assertEquals(403, assertThrows(ApiException.class, () -> reviews.decide(1, choice(2, "APROVAR"))).status());
    }
    @Test void rejectionSupportsCorrectionAndHistory() {
        drafts.submit(1, venue, id, new RoleDraftService.Submission(1));
        reviews.decide(2, choice(2, "REJEITAR"));
        assertTrue(new RoleDAO(source).consultarPagina(null, null, null, 20, 0).isEmpty());
        assertEquals("RASCUNHO", drafts.save(1, venue, id, input(3)).status());
        drafts.submit(1, venue, id, new RoleDraftService.Submission(4));
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, choice(2, "APROVAR"))).status());
        reviews.decide(2, choice(5, "APROVAR"));
        assertEquals(List.of("APROVAR", "REJEITAR"), drafts.reviews(1, venue, id).stream().map(RoleDraftService.Review::decisao).toList());
    }
    @Test void suspensionPreventsPublicationAndLegacyCannotModifyApprovedRole() throws Exception {
        drafts.submit(1, venue, id, new RoleDraftService.Submission(1));
        execute("UPDATE locais SET status='SUSPENSO'");
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, choice(2, "APROVAR"))).status());
        assertTrue(drafts.reviews(1, venue, id).isEmpty());
        execute("UPDATE locais SET status='PUBLICADO'");
        int published = reviews.decide(2, choice(2, "APROVAR")).role().roleId();
        var dao = new RoleDAO(source); var role = dao.buscarPublicadoPorId(published); role.setNome("Tentativa de edição");
        dao.atualizar(role); dao.excluir(published);
        assertEquals("Visita", dao.buscarPublicadoPorId(published).getNome());
        assertNull(dao.buscarPorId(published)); assertTrue(dao.listarTodos().isEmpty());
        execute("UPDATE organizacoes SET status='SUSPENSA'"); assertNull(dao.buscarPublicadoPorId(published));
    }
    @Test void concurrentDecisionsCreateOnePublicationAndOneAudit() throws Exception {
        drafts.submit(1, venue, id, new RoleDraftService.Submission(1));
        execute("UPDATE contas SET administrador=true WHERE id=3");
        var start = new java.util.concurrent.CountDownLatch(1); var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> action = () -> { start.await(); try { reviews.decide(2, choice(2, "APROVAR")); return 200; } catch (ApiException e) { return e.status(); } };
            var first = executor.submit(action); var second = executor.submit(() -> { start.await(); try { reviews.decide(3, choice(2, "APROVAR")); return 200; } catch (ApiException e) { return e.status(); } }); start.countDown();
            assertEquals(Set.of(200, 409), Set.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)));
            assertEquals(1, drafts.reviews(1, venue, id).size());
            assertEquals(1, new RoleDAO(source).consultarPagina(null, null, null, 20, 0).size());
        } finally { executor.shutdownNow(); }
    }
    @Test void auditFailureRollsBackPublication() throws Exception {
        drafts.submit(1, venue, id, new RoleDraftService.Submission(1));
        execute("ALTER TABLE revisoes_roles ADD CONSTRAINT force_audit_failure CHECK (justificativa='Only this reason')");
        assertThrows(RuntimeException.class, () -> reviews.decide(2, choice(2, "APROVAR")));
        assertTrue(new RoleDAO(source).consultarPagina(null, null, null, 20, 0).isEmpty());
        assertEquals("PENDENTE", drafts.list(1, venue, 0).get(0).status());
        assertTrue(drafts.reviews(1, venue, id).isEmpty());
    }
}
