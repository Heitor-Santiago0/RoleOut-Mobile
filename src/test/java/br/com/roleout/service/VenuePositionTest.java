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

class VenuePositionTest {
    JdbcDataSource source; OrganizationService orgs; ReviewService reviews; int org;
    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection()) {
            for (var file : List.of("V001__roles.sql", "V002__organizacoes_e_locais.sql", "V003__sessoes.sql")) {
                try (var r = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + file)), StandardCharsets.UTF_8)) { RunScript.execute(c, r); }
            }
            try (var s = c.createStatement()) { s.executeUpdate("INSERT INTO contas(nome,email,senha_hash,administrador) VALUES ('Owner','o@example.invalid','x',false),('Admin','a@example.invalid','x',true)"); }
        }
        orgs = new OrganizationService(new SqlAccess(source)); reviews = new ReviewService(new SqlAccess(source));
        org = orgs.create(1, new OrganizationService.OrganizationInput("Museu", "MUSEU", null, "o@example.invalid", null)).id();
    }
    OrganizationService.VenueInput input(String lat, String lon) {
        return new OrganizationService.VenueInput("Museu", "Cultura", "Rua 1", "Uberaba", "MG", null, null, null,
                lat == null ? null : new BigDecimal(lat), lon == null ? null : new BigDecimal(lon));
    }
    ReviewService.Item pending(Integer venue) { return reviews.pending(2, 0).stream().filter(i -> Objects.equals(i.localId(), venue)).findFirst().orElseThrow(); }
    void approve(ReviewService.Item item) { reviews.decide(2, new ReviewService.Decision(org, item.localId(), item.versao(), "APROVAR", "Conferido")); }
    @Test void persistsCoordinatesAndSupportsOptionalRemoval() {
        var venue = orgs.saveVenue(1, org, null, input("-19.750001", "-47.930001"));
        var read = orgs.findVenue(1, org, venue.id());
        assertEquals(new BigDecimal("-19.750001"), read.latitude());
        assertEquals(new BigDecimal("-47.930001"), orgs.listVenues(1, org).get(0).longitude());
        orgs.saveVenue(1, org, venue.id(), input(null, null));
        assertNull(orgs.findVenue(1, org, venue.id()).latitude());
        assertNull(orgs.findVenue(1, org, venue.id()).longitude());
    }
    @Test void rejectsIncompleteOutOfRangeAndExcessPrecision() {
        for (String[] point : new String[][]{{"1",null},{null,"1"},{"90.000001","0"},{"0","-180.000001"},{"1.0000001","2"}})
            assertThrows(IllegalArgumentException.class, () -> orgs.saveVenue(1, org, null, input(point[0], point[1])));
        assertTrue(orgs.listVenues(1, org).isEmpty());
        assertEquals(new BigDecimal("0.000000"), orgs.saveVenue(1, org, null, input("0","180")).latitude());
    }
    @Test void reviewedCoordinatesDriveNearbyResultsAndCannotChangeAfterApproval() throws Exception {
        int venue = orgs.saveVenue(1, org, null, input("-19.75", "-47.93")).id();
        try (var c = source.getConnection(); var s = c.prepareStatement("INSERT INTO roles(nome,cidade,categoria,valor,local_id) VALUES ('Visita','Uberaba','Cultura',0,?)")) { s.setInt(1, venue); s.executeUpdate(); }
        var dao = new RoleDAO(source); assertTrue(dao.buscarProximos(-19.75, -47.93, 5).isEmpty());
        orgs.submitVenue(1, org, venue); approve(pending(null));
        var item = pending(venue); assertEquals(new BigDecimal("-19.750000"), item.local().latitude()); approve(item);
        assertEquals(1, dao.buscarProximos(-19.75, -47.93, 5).size());
        assertTrue(dao.buscarProximos(-20, -47.93, 5).isEmpty());
        assertEquals(409, assertThrows(ApiException.class, () -> orgs.saveVenue(1, org, venue, input("0", "0"))).status());
    }
    @Test void coordinateChangeInvalidatesReviewVersion() throws Exception {
        int venue = orgs.saveVenue(1, org, null, input("1", "2")).id();
        orgs.submitVenue(1, org, venue); approve(pending(null)); var stale = pending(venue);
        try (var c = source.getConnection(); var s = c.prepareStatement("UPDATE locais SET latitude=3 WHERE id=?")) { s.setInt(1, venue); s.executeUpdate(); }
        assertEquals(409, assertThrows(ApiException.class, () -> approve(stale)).status());
    }
}
