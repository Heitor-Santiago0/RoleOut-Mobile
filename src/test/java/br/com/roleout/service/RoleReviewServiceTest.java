package br.com.roleout.service;
import br.com.roleout.model.RoleCharacteristics;
import br.com.roleout.model.OpeningHours;

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
            for (var file : List.of("V001__roles.sql", "V002__organizacoes_e_locais.sql", "V009__horario_evento.sql", "V012__termino_evento.sql", "V014__cancelamento_publicado.sql", "V016__data_termino_evento.sql", "V018__funcionamento_evento.sql", "V023__caracteristicas_evento.sql", "V003__sessoes.sql", "V006__rascunhos_roles.sql", "V010__horario_rascunho.sql", "V013__termino_rascunho.sql", "V007__revisao_roles.sql", "V015__revisoes_publicacao.sql", "V017__data_termino_rascunho.sql", "V019__funcionamento_rascunho.sql", "V024__caracteristicas_rascunho.sql", "V004__roteiros.sql", "V008__progresso_roteiro.sql", "V011__horario_parada.sql", "V020__horarios_salvos.sql", "V021__notificacoes_push.sql","V022__paradas_dos_avisos.sql")) {
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
    @Test void characteristicsAreApprovedAuditedAndPreservedForOlderClients() throws Exception {
        var attrs = new RoleCharacteristics(List.of("Rock"), List.of("Japonesa"), List.of("Tranquilo"));
        var changed = drafts.save(1, venue, id, new RoleDraftService.Input("Visita", "Acervo", "Cultura", null, new BigDecimal("12.35"), 1, null, null, false, null, null, null, attrs));
        assertEquals(attrs, changed.caracteristicas());
        assertTrue(new RoleDAO(source).consultarPagina(null, null, null, 20, 0).isEmpty());
        var pending = drafts.submit(1, venue, id, new RoleDraftService.Submission(changed.versao()));
        assertEquals(attrs, reviews.pending(2, 0).get(0).role().caracteristicas());
        var approved = reviews.decide(2, choice(pending.versao(), "APROVAR"));
        int publicId = approved.role().roleId();
        assertEquals(attrs, br.com.roleout.api.RoleDto.from(new RoleDAO(source).buscarPublicadoPorId(publicId)).caracteristicas());
        assertTrue(drafts.reviews(1, venue, id).get(0).conteudo().contains("Japonesa"));
        var legacy = drafts.save(1, venue, id, input(approved.role().versao()));
        assertEquals(attrs, legacy.caracteristicas());
        var empty = new RoleCharacteristics(List.of(), List.of(), List.of());
        changed = drafts.save(1, venue, id, new RoleDraftService.Input("Visita", "Acervo", "Cultura", null, new BigDecimal("12.35"), legacy.versao(), null, null, false, null, null, null, empty));
        pending = drafts.submit(1, venue, id, new RoleDraftService.Submission(changed.versao()));
        assertEquals(attrs, reviews.pending(2, 0).get(0).publicacao().caracteristicas());
        var rejected = reviews.decide(2, choice(pending.versao(), "REJEITAR"));
        assertEquals(attrs, new RoleDAO(source).buscarPublicadoPorId(publicId).getCaracteristicas());
        pending = drafts.submit(1, venue, id, new RoleDraftService.Submission(rejected.role().versao()));
        reviews.decide(2, choice(pending.versao(), "APROVAR"));
        assertEquals(empty, new RoleDAO(source).buscarPublicadoPorId(publicId).getCaracteristicas());
        assertTrue(drafts.reviews(1, venue, id).get(0).conteudo().contains("Publicação anterior:"));
    }
    void execute(String query) throws Exception { try (var c = source.getConnection(); var s = c.createStatement()) { s.executeUpdate(query); } }
    RoleDraftService.Input input(Integer version) { return new RoleDraftService.Input("Visita", "Acervo", "Cultura", null, new BigDecimal("12.35"), version); }
    RoleReviewService.Decision choice(int version, String decision) { return new RoleReviewService.Decision(id, version, decision, "Conferido"); }
    @Test void notificationsWaitForApprovalAndSurviveWorkerRestart() throws Exception {
        var pending=drafts.submit(1,venue,id,new RoleDraftService.Submission(1));
        var published=reviews.decide(2,choice(pending.versao(),"APROVAR"));
        var plans=new ItineraryService(new SqlAccess(source));
        var saved=plans.create(3,new ItineraryService.Input("Visita","Uberaba","2026-10-17","10:00",120,1,2000L,"group",List.of(new ItineraryService.StopInput(published.role().roleId(),60))));
        String session="z".repeat(43);
        execute("INSERT INTO sessoes VALUES ('"+br.com.roleout.security.Tokens.digest(session)+"',3,'2026-10-18 12:00:00')");
        var clock=java.time.Clock.fixed(java.time.Instant.parse("2026-10-16T12:00:00Z"),java.time.ZoneOffset.UTC);
        var sent=new ArrayList<Integer>();
        var receipts=new ArrayList<String>();
        PushGateway gateway=new PushGateway() {
            public Result send(String token,int itinerary) {
                assertEquals("ExpoPushToken[abcdefghijk]",token);
                sent.add(itinerary);return new Result("ok","approval-ticket",null);
            }
            public Result receipt(String ticket) {
                receipts.add(ticket);return new Result("ok",null,null);
            }
        };
        var push=new PushService(new SqlAccess(source),gateway,clock);
        push.register(3,session,new PushService.Registration("ExpoPushToken[abcdefghijk]"));
        var draft=drafts.save(1,venue,id,new RoleDraftService.Input("Visita","Acervo","Cultura","2026-10-17",new BigDecimal("12.35"),published.role().versao(),"10:00","20:00"));
        pending=drafts.submit(1,venue,id,new RoleDraftService.Submission(draft.versao()));
        push.collect();push.dispatch();assertTrue(sent.isEmpty());
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM push_envios")){r.next();assertEquals(0,r.getInt(1));}
        reviews.decide(2,choice(pending.versao(),"APROVAR"));push.collect();
        new PushService(new SqlAccess(source),null,clock).collect();
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM push_envios")){r.next();assertEquals(1,r.getInt(1));}
        var restarted=new PushService(new SqlAccess(source),gateway,clock);
        restarted.dispatch();restarted.dispatch();
        assertEquals(List.of(saved.roteiro().id()),sent);
        assertTrue(receipts.isEmpty());
        new PushService(new SqlAccess(source),gateway,java.time.Clock.offset(clock,java.time.Duration.ofMinutes(16))).dispatch();
        assertEquals(List.of("approval-ticket"),receipts);
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT estado FROM push_envios")){r.next();assertEquals("ACEITO_PROVEDOR",r.getString(1));}
        assertEquals(saved,plans.find(3,saved.roteiro().id()));
        assertTrue(plans.check(3,saved.roteiro().id()).alertas().stream().anyMatch(a->a.codigo().equals("HORARIOS_ALTERADOS")));
        assertEquals(404,assertThrows(ApiException.class,()->plans.find(1,saved.roteiro().id())).status());
    }
    @Test void openingHoursRemainPrivateUntilApprovedAndAmendmentsAreAudited() {
        var hours = List.of(new OpeningHours.Rule("5",List.of(new OpeningHours.Window("09:00","18:00"))));
        var draft = drafts.save(1,venue,id,new RoleDraftService.Input("Museu",null,"Cultura",null,BigDecimal.ZERO,1,null,null,false,null,null,hours));
        assertEquals(hours,drafts.list(1,venue,0).get(0).funcionamento());
        var pending = drafts.submit(1,venue,id,new RoleDraftService.Submission(draft.versao()));
        var published = reviews.decide(2,choice(pending.versao(),"APROVAR"));
        var dao = new RoleDAO(source); int publicId=published.role().roleId();
        assertEquals(hours,dao.buscarPublicadoPorId(publicId).getFuncionamento());
        var closed = List.of(new OpeningHours.Rule("2026-10-16",List.<OpeningHours.Window>of()));
        draft = drafts.save(1,venue,id,new RoleDraftService.Input("Museu",null,"Cultura",null,BigDecimal.ZERO,published.role().versao(),null,null,false,null,null,closed));
        assertEquals(hours,dao.buscarPublicadoPorId(publicId).getFuncionamento());
        pending = drafts.submit(1,venue,id,new RoleDraftService.Submission(draft.versao()));
        var rejected = reviews.decide(2,choice(pending.versao(),"REJEITAR"));
        assertEquals(hours,dao.buscarPublicadoPorId(publicId).getFuncionamento());
        draft = drafts.save(1,venue,id,new RoleDraftService.Input("Museu",null,"Cultura",null,BigDecimal.ZERO,rejected.role().versao(),null,null,false,null,null,closed));
        pending = drafts.submit(1,venue,id,new RoleDraftService.Submission(draft.versao()));
        var changed = reviews.decide(2,choice(pending.versao(),"APROVAR"));
        assertEquals(publicId,changed.role().roleId());
        assertEquals(closed,br.com.roleout.api.RoleDto.from(dao.buscarPublicadoPorId(publicId)).funcionamento());
        var audit=drafts.reviews(1,venue,id).get(0).conteudo();
        assertTrue(audit.contains("09:00")); assertTrue(audit.contains("2026-10-16: Fechado"));
    }
    @Test void endDateTravelsThroughApprovalCatalogAmendmentAndAudit() {
        var changed = drafts.save(1, venue, id, new RoleDraftService.Input("Festival", null, "Cultura", "2027-12-31", BigDecimal.ZERO, 1, "22:00", "02:00", false, null, "2028-01-02"));
        var pending = drafts.submit(1, venue, id, new RoleDraftService.Submission(changed.versao()));
        var published = reviews.decide(2, choice(pending.versao(), "APROVAR"));
        var dao = new RoleDAO(source);
        assertEquals("2028-01-02", published.publicacao().dataFim());
        assertEquals("2028-01-02", br.com.roleout.api.RoleDto.from(dao.buscarPublicadoPorId(published.role().roleId())).dataFim());
        assertEquals(1, dao.consultarPagina(null, null, null, 20, 0, java.time.LocalDate.parse("2028-01-01")).size());
        assertEquals(0, dao.consultarPagina(null, null, null, 20, 0, java.time.LocalDate.parse("2028-01-03")).size());
        var edit = drafts.save(1, venue, id, new RoleDraftService.Input("Festival", null, "Cultura", "2027-12-31", BigDecimal.ZERO, published.role().versao(), "22:00", "03:00", false, null, "2028-01-03"));
        assertEquals("2028-01-02", dao.buscarPublicadoPorId(published.role().roleId()).getDataFim().toString());
        pending = drafts.submit(1, venue, id, new RoleDraftService.Submission(edit.versao()));
        var updated = reviews.decide(2, choice(pending.versao(), "APROVAR"));
        assertEquals(published.role().roleId(), updated.role().roleId());
        assertEquals("2028-01-03", updated.publicacao().dataFim());
        String audit = drafts.reviews(1, venue, id).get(0).conteudo();
        assertTrue(audit.contains("2028-01-02")); assertTrue(audit.contains("2028-01-03"));
    }
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
        assertEquals("RASCUNHO", drafts.save(1, venue, id, input(3)).status());
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, choice(2, "APROVAR"))).status());
        assertEquals(1, dao.consultarPagina(null, null, null, 20, 0).size());
    }
    int publish() {
        drafts.submit(1, venue, id, new RoleDraftService.Submission(1));
        return reviews.decide(2, choice(2, "APROVAR")).role().roleId();
    }
    RoleDraftService.Input proposal(int version, boolean cancel) {
        return new RoleDraftService.Input("Visita atualizada", "Novo acervo", "Cultura", "2027-11-02", new BigDecimal("18.50"), version, "14:00", "17:00", cancel, cancel ? "Manutenção do local" : null);
    }
    @Test void amendmentKeepsApprovedDataUntilReviewAndRetainsPublicId() {
        int publicId = publish(); var dao = new RoleDAO(source);
        drafts.save(1, venue, id, proposal(3, false));
        assertEquals("Visita", dao.buscarPublicadoPorId(publicId).getNome());
        drafts.submit(1, venue, id, new RoleDraftService.Submission(4));
        var pending = reviews.pending(2, 0).get(0);
        assertEquals("Visita", pending.publicacao().nome());
        assertEquals("Visita atualizada", pending.role().nome());
        assertEquals("Visita", dao.buscarPublicadoPorId(publicId).getNome());
        var approved = reviews.decide(2, choice(5, "APROVAR"));
        assertEquals(publicId, approved.role().roleId());
        var changed = dao.buscarPublicadoPorId(publicId);
        assertEquals("Visita atualizada", changed.getNome());
        assertEquals("2027-11-02", changed.getData().toString());
        assertEquals("17:00", changed.getHorarioFim());
        assertEquals(18.50, changed.getValor());
        assertEquals(1, dao.consultarPagina(null, null, null, 20, 0).size());
        var history = drafts.reviews(1, venue, id);
        assertTrue(history.get(0).conteudo().contains("Novo acervo"));
        assertFalse(history.get(1).conteudo().contains("Novo acervo"));
        assertEquals(409, assertThrows(ApiException.class, () -> reviews.decide(2, choice(5, "APROVAR"))).status());
    }
    @Test void rejectedCancellationKeepsPublicationAndCanBeCorrectedThenReactivated() throws Exception {
        int publicId = publish(); var dao = new RoleDAO(source);
        execute("UPDATE locais SET latitude=-19.75,longitude=-47.93 WHERE id=" + venue);
        drafts.save(1, venue, id, proposal(3, true));
        drafts.submit(1, venue, id, new RoleDraftService.Submission(4));
        assertFalse(dao.buscarPublicadoPorId(publicId).isCancelado());
        var rejected = reviews.decide(2, choice(5, "REJEITAR"));
        assertEquals(publicId, rejected.role().roleId());
        assertFalse(dao.buscarPublicadoPorId(publicId).isCancelado());
        assertEquals(1, dao.buscarProximos(-19.75, -47.93, 5).size());
        drafts.save(1, venue, id, proposal(6, true));
        drafts.submit(1, venue, id, new RoleDraftService.Submission(7));
        reviews.decide(2, choice(8, "APROVAR"));
        assertTrue(dao.buscarPublicadoPorId(publicId).isCancelado());
        assertEquals("Manutenção do local", br.com.roleout.api.RoleDto.from(dao.buscarPublicadoPorId(publicId)).motivoCancelamento());
        assertTrue(dao.consultarPagina(null, null, null, 20, 0).isEmpty());
        assertTrue(dao.buscarProximos(-19.75, -47.93, 5).isEmpty());
        drafts.save(1, venue, id, proposal(9, false));
        drafts.submit(1, venue, id, new RoleDraftService.Submission(10));
        assertTrue(dao.buscarPublicadoPorId(publicId).isCancelado());
        reviews.decide(2, choice(11, "APROVAR"));
        assertFalse(dao.buscarPublicadoPorId(publicId).isCancelado());
        assertNull(dao.buscarPublicadoPorId(publicId).getMotivoCancelamento());
    }
    @Test void cancellationRequiresPublishedEventReasonAndOwnerAndRejectsStaleEdits() {
        assertThrows(ApiException.class, () -> drafts.save(1, venue, id, proposal(1, true)));
        publish();
        assertEquals(404, assertThrows(ApiException.class, () -> drafts.save(3, venue, id, proposal(3, true))).status());
        assertThrows(IllegalArgumentException.class, () -> drafts.save(1, venue, id,
            new RoleDraftService.Input("Visita", null, "Cultura", null, BigDecimal.ZERO, 3, null, null, true, " ")));
        drafts.save(1, venue, id, proposal(3, true));
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, id, proposal(3, false))).status());
        drafts.submit(1, venue, id, new RoleDraftService.Submission(4));
        assertEquals(409, assertThrows(ApiException.class, () -> drafts.save(1, venue, id, proposal(5, false))).status());
    }
    @Test void failedAuditRollsBackAnAmendment() throws Exception {
        int publicId = publish();
        drafts.save(1, venue, id, proposal(3, true));
        drafts.submit(1, venue, id, new RoleDraftService.Submission(4));
        execute("ALTER TABLE revisoes_roles ADD CONSTRAINT no_new_review CHECK (versao < 5)");
        assertThrows(RuntimeException.class, () -> reviews.decide(2, choice(5, "APROVAR")));
        var original = new RoleDAO(source).buscarPublicadoPorId(publicId);
        assertEquals("Visita", original.getNome()); assertFalse(original.isCancelado());
        assertEquals("PENDENTE", drafts.list(1, venue, 0).get(0).status());
        assertEquals(1, drafts.reviews(1, venue, id).size());
    }
    @Test void publicationPreservesOwnerEventTime() {
        var edited = drafts.save(1, venue, id, new RoleDraftService.Input("Show", null, "Cultura", "2027-10-13", BigDecimal.ZERO, 1, "19:30", "21:00"));
        drafts.submit(1, venue, id, new RoleDraftService.Submission(edited.versao()));
        var published = reviews.decide(2, choice(3, "APROVAR"));
        assertEquals("21:00", br.com.roleout.api.RoleDto.from(new RoleDAO(source).buscarPublicadoPorId(published.role().roleId())).horarioFim());
        assertEquals("19:30", new RoleDAO(source).buscarPublicadoPorId(published.role().roleId()).getHorario());
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
