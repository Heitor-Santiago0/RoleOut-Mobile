package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.security.Passwords;
import br.com.roleout.security.Tokens;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationSecurityTest {
    private JdbcDataSource source;
    private AuthService auth;
    private OrganizationService organizations;
    private static final String PASSWORD = "uma senha longa de teste";
    @BeforeEach
    void schema() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection()) {
            for (String file : new String[]{"V001__roles.sql", "V002__organizacoes_e_locais.sql", "V009__horario_evento.sql", "V012__termino_evento.sql", "V014__cancelamento_publicado.sql", "V016__data_termino_evento.sql", "V018__funcionamento_evento.sql", "V023__caracteristicas_evento.sql", "V003__sessoes.sql"}) {
                try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(
                        getClass().getResourceAsStream("/db/migration/" + file)), StandardCharsets.UTF_8)) {
                    RunScript.execute(c, reader);
                }
            }
        }
        SqlAccess sql = new SqlAccess(source);
        auth = new AuthService(sql);
        organizations = new OrganizationService(sql);
    }
    private AuthService.Account account(String email) {
        return auth.signup(new AuthService.Signup("Responsável teste", email, PASSWORD));
    }
    private OrganizationService.OrganizationInput organizationInput() {
        return new OrganizationService.OrganizationInput("Museu teste", "MUSEU", null, "contato@example.com", null);
    }
    private OrganizationService.VenueInput venueInput(String name) {
        return new OrganizationService.VenueInput(name, "Cultura", "Rua teste 10", "Uberaba", "MG", "38000-000", null, "Rampa");
    }
    @Test void sixCharacterPasswordsWorkAndBoundariesRemainEnforced() {
        var account=auth.signup(new AuthService.Signup("Seis", "six@example.com", "abcdef"));
        assertEquals(account.id(),auth.login(new AuthService.Login("six@example.com","abcdef")).conta().id());
        assertThrows(IllegalArgumentException.class,()->Inputs.password("abcde"));
        assertThrows(IllegalArgumentException.class,()->Inputs.password("x".repeat(129)));
        assertEquals("x".repeat(128),Inputs.password("x".repeat(128)));
    }
    @Test
    void profileNamePersistsAcrossLoginAndOnlyChangesAuthenticatedAccount() {
        var first = account("first@example.com");
        var other = account("other@example.com");
        var session = auth.login(new AuthService.Login(first.email(), PASSWORD));
        var updated = auth.updateProfile(session.token(), new AuthService.ProfileInput("  Novo nome  "));
        assertEquals(new AuthService.Account(first.id(), "Novo nome", first.email(), false, false), updated);
        assertEquals(updated, auth.authenticate(session.token()));
        assertEquals(updated, auth.login(new AuthService.Login(first.email(), PASSWORD)).conta());
        assertEquals(other, auth.login(new AuthService.Login(other.email(), PASSWORD)).conta());
        for (String invalid : new String[]{null, " ", "x".repeat(201)}) {
            assertThrows(IllegalArgumentException.class, () -> auth.updateProfile(session.token(), new AuthService.ProfileInput(invalid)));
        }
        assertEquals(updated, auth.authenticate(session.token()));
        auth.logout(session.token());
        assertEquals(401, assertThrows(ApiException.class, () -> auth.updateProfile(session.token(), new AuthService.ProfileInput("Negado"))).status());
        assertEquals(updated, auth.login(new AuthService.Login(first.email(), PASSWORD)).conta());
    }
    @Test
    void signupHashesPasswordAndLoginRevocationWorks() throws Exception {
        var account = account(" USER@EXAMPLE.COM ");
        assertEquals("user@example.com", account.email());
        assertFalse(account.administrador());
        assertFalse(account.emailConfirmado());
        try (var c = source.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("SELECT senha_hash FROM contas")) {
            rs.next();
            assertNotEquals(PASSWORD, rs.getString(1));
            assertTrue(Passwords.verify(PASSWORD, rs.getString(1)));
        }
        var session = auth.login(new AuthService.Login("user@example.com", PASSWORD));
        assertEquals(account.id(), auth.authenticate(session.token()).id());
        try (var c = source.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("SELECT token_hash FROM sessoes")) {
            rs.next();
            assertNotEquals(session.token(), rs.getString(1));
            assertEquals(Tokens.digest(session.token()), rs.getString(1));
        }
        auth.logout(session.token());
        assertEquals(401, assertThrows(ApiException.class, () -> auth.authenticate(session.token())).status());
    }
    @Test
    void invalidPasswordsDuplicateEmailsAndExpiredSessionsAreRejected() throws Exception {
        account("user@example.com");
        assertEquals(409, assertThrows(ApiException.class, () -> account("USER@example.com")).status());
        assertEquals(401, assertThrows(ApiException.class, () -> auth.login(new AuthService.Login("user@example.com", "senha incorreta longa"))).status());
        assertEquals(401, assertThrows(ApiException.class, () -> auth.login(new AuthService.Login("missing@example.com", PASSWORD))).status());
        assertThrows(IllegalArgumentException.class, () -> auth.signup(new AuthService.Signup("A", "a@example.com", "curta")));
        var session = auth.login(new AuthService.Login("user@example.com", PASSWORD));
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("UPDATE sessoes SET expira_em='2000-01-01 00:00:00'");
        }
        assertEquals(401, assertThrows(ApiException.class, () -> auth.authenticate(session.token())).status());
        assertEquals(401, assertThrows(ApiException.class, () -> auth.authenticate("invalid")).status());
    }
    @Test
    void creatingOrganizationAtomicallyAssignsOwnerAndDraftRemainsPrivate() {
        var owner = account("owner@example.com");
        var other = account("other@example.com");
        var org = organizations.create(owner.id(), organizationInput());
        assertEquals("RESPONSAVEL", org.papel());
        assertEquals("PENDENTE", org.status());
        assertEquals(1, organizations.list(owner.id()).size());
        assertTrue(organizations.list(other.id()).isEmpty());
        var venue = organizations.saveVenue(owner.id(), org.id(), null, venueInput("Local A"));
        assertEquals("RASCUNHO", venue.status());
        assertEquals(404, assertThrows(ApiException.class, () -> organizations.find(other.id(), org.id())).status());
        assertEquals(404, assertThrows(ApiException.class, () -> organizations.listVenues(other.id(), org.id())).status());
        assertEquals(404, assertThrows(ApiException.class, () -> organizations.saveVenue(other.id(), org.id(), venue.id(), venueInput("Ataque"))).status());
        assertEquals("Local A", organizations.findVenue(owner.id(), org.id(), venue.id()).nome());
    }
    @Test
    void failedMembershipRollsBackOrganizationCreation() throws Exception {
        assertThrows(RuntimeException.class, () -> organizations.create(999, organizationInput()));
        try (var c = source.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("SELECT COUNT(*) FROM organizacoes")) {
            rs.next(); assertEquals(0, rs.getInt(1));
        }
    }
    @Test
    void editorCannotEditOrganizationAndVenueCannotMoveAcrossOrganizations() throws Exception {
        var owner = account("owner@example.com");
        var editor = account("editor@example.com");
        var org = organizations.create(owner.id(), organizationInput());
        var otherOrg = organizations.create(owner.id(), organizationInput());
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("INSERT INTO membros_organizacao VALUES (" + org.id() + "," + editor.id() + ",'EDITOR')");
        }
        assertEquals(403, assertThrows(ApiException.class, () -> organizations.update(editor.id(), org.id(), organizationInput())).status());
        var venue = organizations.saveVenue(editor.id(), org.id(), null, venueInput("Local A"));
        assertEquals(404, assertThrows(ApiException.class, () -> organizations.saveVenue(owner.id(), otherOrg.id(), venue.id(), venueInput("Movido"))).status());
        assertEquals(org.id(), organizations.findVenue(owner.id(), org.id(), venue.id()).organizacaoId());
    }
    @Test
    void publishedVenuesAndSuspendedOrganizationsCannotBeChangedThroughDraftApi() throws Exception {
        var owner = account("owner@example.com");
        var org = organizations.create(owner.id(), organizationInput());
        var venue = organizations.saveVenue(owner.id(), org.id(), null, venueInput("Local A"));
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("UPDATE locais SET status='PUBLICADO'");
        }
        assertEquals(409, assertThrows(ApiException.class, () -> organizations.saveVenue(owner.id(), org.id(), venue.id(), venueInput("Editado"))).status());
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("UPDATE organizacoes SET status='SUSPENSA'");
        }
        assertEquals(409, assertThrows(ApiException.class, () -> organizations.saveVenue(owner.id(), org.id(), null, venueInput("Novo"))).status());
    }
    @Test
    void hashesUseDistinctSaltsAndMalformedHashesAreRejected() {
        String first = Passwords.hash(PASSWORD);
        String second = Passwords.hash(PASSWORD);
        assertNotEquals(first, second);
        assertTrue(Passwords.verify(PASSWORD, first));
        assertFalse(Passwords.verify("senha errada", first));
        assertFalse(Passwords.verify(PASSWORD, "broken$hash"));
        assertFalse(Passwords.verify(PASSWORD, first.replace("600000", "999999999")));
    }
}
