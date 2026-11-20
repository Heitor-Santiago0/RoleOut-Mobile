package br.com.roleout.service;
import br.com.roleout.model.RoleCharacteristics;
import br.com.roleout.model.OpeningHours;

import br.com.roleout.dao.*;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscoverySuggestionsTest {
    JdbcDataSource db; SqlAccess sql; RoleDAO roles; DiscoverySuggestions service;
    Instant instant=Instant.parse("2026-10-20T15:00:00Z");
    DiscoverySuggestions.Context here=new DiscoverySuggestions.Context(-19.75,-47.93,"Uberaba","America/Sao_Paulo");
    @BeforeEach void setup() throws Exception {
        db=new JdbcDataSource(); db.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        try(var c=db.getConnection()) {
            for(String name:List.of("V001__roles.sql","V002__organizacoes_e_locais.sql","V005__preferencias.sql","V009__horario_evento.sql","V012__termino_evento.sql","V014__cancelamento_publicado.sql","V016__data_termino_evento.sql","V018__funcionamento_evento.sql","V023__caracteristicas_evento.sql","V025__sugestoes_automaticas.sql","V026__frequencia_sugestoes.sql"))
                try(var r=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/"+name)),StandardCharsets.UTF_8)){RunScript.execute(c,r);}
            c.createStatement().executeUpdate("INSERT INTO contas(nome,email,senha_hash) VALUES ('A','a@example.invalid','x'),('B','b@example.invalid','x')");
        }
        sql=new SqlAccess(db);roles=mock(RoleDAO.class);service=at(instant);
        new PreferencesService(sql).save(1,new PreferencesService.Input(false,List.of("Rock"),List.of("Japonesa"),List.of(),List.of("Tranquilo")));
        when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(point(1,"Uberaba","Rock","10:00","18:00",null)));
    }
    DiscoverySuggestions at(Instant now){return new DiscoverySuggestions(sql,roles,Clock.fixed(now,ZoneOffset.UTC));}
    RoleDAO.Ponto point(int id,String city,String music,String start,String end,List<OpeningHours.Rule> hours){
        return new RoleDAO.Ponto(id,"Show","Música",-19.75,-47.93,1,city,"2026-10-20",start,null,end,hours,new RoleCharacteristics(List.of(music),List.of(),List.of()));
    }
    void enable(boolean trips,int interval){service.save(1,new DiscoverySuggestions.Settings(true,trips,interval,"Uberaba"));}
    @Test void defaultsArePreparedButRequireCityAndSettingsArePrivateValidatedAndPersistent() {
        assertTrue(service.get(1).ativo());assertTrue(service.get(1).viagens());assertEquals(0,service.get(1).intervaloHoras());assertNull(service.evaluate(1,here).sugestao());verifyNoInteractions(roles);
        enable(false,72);assertEquals(service.get(1),at(instant).get(1));assertNull(service.get(2).cidadeBase());
        assertThrows(IllegalArgumentException.class,()->service.save(1,new DiscoverySuggestions.Settings(true,true,1,"Uberaba")));
        assertThrows(IllegalArgumentException.class,()->service.save(1,new DiscoverySuggestions.Settings(true,true,24," ")));
        assertEquals(72,service.get(1).intervaloHoras());
        service.save(1,new DiscoverySuggestions.Settings(false,false,24,"Uberaba"));assertNull(service.get(1).cidadeBase());
    }
    @Test void frequencySurvivesRestartAndTogglingAndIsAtomicAcrossDevices() throws Exception {
        enable(false,24);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<DiscoverySuggestions.Result> action=()->{start.await();return at(instant).evaluate(1,here);};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertEquals(1,(a.get().sugestao()!=null?1:0)+(b.get().sugestao()!=null?1:0));
        } finally {pool.shutdownNow();}
        assertNull(at(instant.plusSeconds(3600)).evaluate(1,here).sugestao());
        service.save(1,new DiscoverySuggestions.Settings(false,false,24,null));enable(false,24);
        assertNull(service.evaluate(1,here).sugestao());
        // No quota reset by disabling, changing interests, restarting, or visiting another city.
        when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(new RoleDAO.Ponto(2,"Bar","Música",-19.75,-47.93,1,"Uberaba",null,null,null,null,List.of(new OpeningHours.Rule("3",List.of(new OpeningHours.Window("10:00","18:00")))),new RoleCharacteristics(List.of("Rock"),List.of(),List.of()))));
        assertNotNull(at(instant.plus(Duration.ofHours(24))).evaluate(1,here).sugestao());
    }
    @Test void travelRequiresSeparateOptInAndCityMustMatchCatalog() {
        service.save(1,new DiscoverySuggestions.Settings(true,false,24,"São Paulo"));
        assertNull(service.evaluate(1,here).sugestao());
        service.save(1,new DiscoverySuggestions.Settings(true,true,24,"São Paulo"));
        var alert=service.evaluate(1,here).sugestao();assertNotNull(alert);assertTrue(alert.outraCidade());
        assertNull(service.evaluate(2,here).sugestao());
    }
    @Test void knownScheduleAndExactInterestsAreRequiredAndEmptyChecksDoNotConsumeQuota() {
        enable(false,24);
        for(var p:List.of(point(1,"Uberaba","Pop","10:00","18:00",null),point(2,"Uberaba","Rock",null,null,null),point(3,"Uberaba","Rock","13:00","18:00",null),point(4,"Uberaba","Rock","10:00","12:00",null),point(5,"Outra cidade","Rock","10:00","18:00",null))) {
            when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(p));assertNull(service.evaluate(1,here).sugestao());
        }
        when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(point(6,"Uberaba","Rock","12:00","18:00",null)));
        assertEquals(6,service.evaluate(1,here).sugestao().roleId());
    }
    @Test void closingExceptionOverridesWeeklyHoursAndEventBounds() {
        var hours=List.of(new OpeningHours.Rule("2",List.of(new OpeningHours.Window("09:00","18:00"))),new OpeningHours.Rule("2026-10-20",List.of()));
        assertFalse(DiscoverySuggestions.availableNow(point(1,"Uberaba","Rock","10:00","18:00",hours),instant.atZone(ZoneId.of("America/Sao_Paulo"))));
        var pause=List.of(new OpeningHours.Rule("2",List.of(new OpeningHours.Window("09:00","12:00"),new OpeningHours.Window("13:00","18:00"))));
        assertFalse(DiscoverySuggestions.availableNow(point(1,"Uberaba","Rock","10:00","18:00",pause),instant.atZone(ZoneId.of("America/Sao_Paulo"))));
    }
    @Test void rankingCountsDimensionsThenDistanceAndSkippedPreferencesSuppressSuggestions() {
        enable(false,24);
        var better=new RoleDAO.Ponto(2,"Bar","Música",-19.75,-47.93,3,"Uberaba","2026-10-20","10:00",null,"18:00",null,new RoleCharacteristics(List.of("Rock"),List.of("Japonesa"),List.of("Tranquilo")));
        when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(point(1,"Uberaba","Rock","10:00","18:00",null),better));
        new PreferencesService(sql).save(1,new PreferencesService.Input(true,List.of(),List.of(),List.of(),List.of()));assertNull(service.evaluate(1,here).sugestao());
        new PreferencesService(sql).save(1,new PreferencesService.Input(false,List.of("Rock"),List.of("Japonesa"),List.of(),List.of("Tranquilo")));
        assertEquals(2,service.evaluate(1,here).sugestao().roleId());
    }
    @Test void realCatalogExcludesCancelledUnapprovedAndDistantRoles() throws Exception {
        try(var c=db.getConnection();var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO organizacoes(nome,tipo,contato_email,status) VALUES ('Teste','BAR','o@example.invalid','APROVADA')");
            s.executeUpdate("INSERT INTO locais(organizacao_id,nome,categoria,endereco,cidade,uf,latitude,longitude,status) VALUES (1,'Local','Música','Rua 1','Uberaba','MG',-19.75,-47.93,'PUBLICADO')");
            s.executeUpdate("INSERT INTO roles(nome,cidade,categoria,valor,local_id,data,horario,horario_fim) VALUES ('Show','Uberaba','Música',0,1,'2026-10-20','10:00','18:00')");
            try(var update=c.prepareStatement("UPDATE roles SET caracteristicas=?")) {update.setString(1,RoleCharacteristics.encode(new RoleCharacteristics(List.of("Rock"),List.of(),List.of())));update.executeUpdate();}
        }
        var real=new DiscoverySuggestions(sql,new RoleDAO(db),Clock.fixed(instant,ZoneOffset.UTC));enable(false,24);
        try(var c=db.getConnection();var s=c.createStatement()) {
            s.executeUpdate("UPDATE roles SET cancelado=TRUE");assertNull(real.evaluate(1,here).sugestao());
            s.executeUpdate("UPDATE roles SET cancelado=FALSE");s.executeUpdate("UPDATE locais SET status='PENDENTE'");assertNull(real.evaluate(1,here).sugestao());
            s.executeUpdate("UPDATE locais SET status='PUBLICADO',latitude=-20.75");assertNull(real.evaluate(1,here).sugestao());
            s.executeUpdate("UPDATE locais SET latitude=-19.75");assertNotNull(real.evaluate(1,here).sugestao());
        }
    }

    @Test void dailyPolicyAllowsThreeThenRequiresAllThreeDimensionsAndNeverRepeats() {
        enable(true,0);
        for(int i=0;i<3;i++) {
            when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(point(i+1,"Uberaba","Rock","00:00","23:59",null)));
            assertNotNull(at(instant.plus(Duration.ofHours(i))).evaluate(1,here).sugestao());
        }
        when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(point(4,"Uberaba","Rock","00:00","23:59",null)));
        assertNull(at(instant.plus(Duration.ofHours(3))).evaluate(1,here).sugestao());
        var best=new RoleDAO.Ponto(4,"Bar","Música",-19.75,-47.93,1,"Uberaba","2026-10-20","00:00",null,"23:59",null,new RoleCharacteristics(List.of("Rock"),List.of("Japonesa"),List.of("Tranquilo")));
        when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(best));
        assertNotNull(at(instant.plus(Duration.ofHours(3))).evaluate(1,here).sugestao());
        assertNull(at(instant.plus(Duration.ofHours(4))).evaluate(1,here).sugestao());
        service.save(1,new DiscoverySuggestions.Settings(false,true,0,null));enable(true,0);
        assertNull(at(instant.plus(Duration.ofHours(4))).evaluate(1,here).sugestao());
    }
    @Test void dailyQuotaUsesServerRollingWindowAndOneHourSpacingAcrossDevices() throws Exception {
        enable(true,0);
        assertNotNull(service.evaluate(1,here).sugestao());
        when(roles.buscarProximos(-19.75,-47.93,5)).thenReturn(List.of(point(2,"Uberaba","Rock","00:00","23:59",null)));
        assertNull(at(instant.plusSeconds(3599)).evaluate(1,here).sugestao());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->at(instant.plusSeconds(3600)).evaluate(1,here));
            var b=pool.submit(()->at(instant.plusSeconds(3600)).evaluate(1,here));
            assertEquals(1,(a.get().sugestao()!=null?1:0)+(b.get().sugestao()!=null?1:0));
        } finally {pool.shutdownNow();}
        try(var c=db.getConnection();var st=c.createStatement();var r=st.executeQuery("SELECT COUNT(*) FROM sugestoes_entregas WHERE conta_id=1")){r.next();assertEquals(2,r.getInt(1));}
    }
    @Test void rejectsInvalidPositionOrTimezoneBeforeLookup() {
        for(var context:List.of(new DiscoverySuggestions.Context(Double.NaN,0d,"A","UTC"),new DiscoverySuggestions.Context(91d,0d,"A","UTC"),new DiscoverySuggestions.Context(0d,181d,"A","UTC"),new DiscoverySuggestions.Context(0d,0d,"A","fake/zone")))
            assertThrows(IllegalArgumentException.class,()->service.evaluate(1,context));
        verifyNoInteractions(roles);
    }
}
