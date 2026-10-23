package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.security.Tokens;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PushServiceTest {
    JdbcDataSource source;PushService push;ItineraryService plans;
    final String session="a".repeat(43), other="b".repeat(43), token="ExpoPushToken[0123456789abcdef]";
    int route,sends,receipts;Instant instant=Instant.parse("2026-10-16T12:00:00Z");
    PushGateway.Result sendResult=new PushGateway.Result("ok","ticket",null),receiptResult=new PushGateway.Result("ok",null,null);
    boolean offline;
    Clock clock=new Clock() { public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return Clock.fixed(instant,z);} public Instant instant(){return instant;} };
    @BeforeEach void setup() throws Exception {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection()) {
            for(String file:List.of("V001__roles.sql","V002__organizacoes_e_locais.sql","V003__sessoes.sql","V004__roteiros.sql","V008__progresso_roteiro.sql","V009__horario_evento.sql","V011__horario_parada.sql","V012__termino_evento.sql","V014__cancelamento_publicado.sql","V016__data_termino_evento.sql","V018__funcionamento_evento.sql", "V023__caracteristicas_evento.sql","V020__horarios_salvos.sql","V021__notificacoes_push.sql","V022__paradas_dos_avisos.sql"))
                try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/"+file)),StandardCharsets.UTF_8)) {RunScript.execute(c,reader);}
        }
        execute("INSERT INTO contas(nome,email,senha_hash) VALUES ('A','a@test.com','test'),('B','b@test.com','test')");
        execute("INSERT INTO sessoes VALUES ('"+Tokens.digest(session)+"',1,'2026-10-20 12:00:00'),('"+Tokens.digest(other)+"',2,'2026-10-20 12:00:00')");
        execute("INSERT INTO roles(nome,cidade,local,valor,categoria) VALUES ('Museu','Uberaba','Centro',0,'Cultura')");
        plans=new ItineraryService(new SqlAccess(source));
        route=plans.create(1,new ItineraryService.Input("Passeio","Uberaba","2026-10-17","10:00",120,1,0L,"group",List.of(new ItineraryService.StopInput(1,60)))).roteiro().id();
        push=new PushService(new SqlAccess(source),new PushGateway(){
            public Result send(String t,int id) throws Exception {sends++;assertEquals(token,t);assertEquals(route,id);if(offline)throw new java.io.IOException();return sendResult;}
            public Result receipt(String t){receipts++;assertEquals("ticket",t);return receiptResult;}
        },clock);
    }
    void execute(String query)throws Exception {try(var c=source.getConnection();var s=c.createStatement()){s.executeUpdate(query);}}
    int count(String table)throws Exception {try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getInt(1);}}
    String state()throws Exception {try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT estado FROM push_envios ORDER BY id DESC LIMIT 1")){r.next();return r.getString(1);}}
    void register(){push.register(1,session,new PushService.Registration(token));}
    void change()throws Exception {execute("UPDATE roles SET horario_fim='20:00'");push.collect();}
    void secondStop()throws Exception {
        execute("INSERT INTO roles(nome,cidade,local,valor,categoria) VALUES ('Jardim','Uberaba','Centro',0,'Cultura')");
        execute("INSERT INTO roteiro_paradas(roteiro_id,ordem,role_id,nome,minutos,preco_centavos,concluida) VALUES ("+route+",2,2,'Jardim',30,0,FALSE)");
    }
    @Test void completingAffectedStopCancelsEvenWithAnotherStopPending()throws Exception {
        secondStop();register();execute("UPDATE roles SET horario_fim='20:00' WHERE id=1");push.collect();
        plans.progress(1,route,1,new ItineraryService.Progress(true));
        push.collect();push.dispatch();assertEquals(0,sends);assertEquals("CANCELADO",state());
    }
    @Test void removingAffectedStopCancelsQueuedRetry()throws Exception {
        secondStop();register();execute("UPDATE roles SET horario_fim='20:00' WHERE id=1");push.collect();
        offline=true;push.dispatch();assertEquals(1,sends);
        execute("DELETE FROM roteiro_paradas WHERE roteiro_id="+route+" AND role_id=1");
        instant=instant.plusSeconds(121);offline=false;push.dispatch();
        assertEquals(1,sends);assertEquals("CANCELADO",state());
    }
    @Test void remainingAffectedStopStillReceivesOneNotification()throws Exception {
        secondStop();register();change();plans.progress(1,route,1,new ItineraryService.Progress(true));
        push.dispatch();push.dispatch();assertEquals(1,sends);assertEquals("RECIBO",state());
    }
    @Test void unrelatedCompletedStopDoesNotSuppressAlert()throws Exception {
        secondStop();register();execute("UPDATE roles SET horario_fim='20:00' WHERE id=1");push.collect();
        plans.progress(1,route,2,new ItineraryService.Progress(true));
        push.dispatch();assertEquals(1,sends);
    }
    @Test void sentMessageStillGetsReceiptAfterAffectedStopIsCompleted()throws Exception {
        secondStop();register();execute("UPDATE roles SET horario_fim='20:00' WHERE id=1");push.collect();push.dispatch();
        plans.progress(1,route,1,new ItineraryService.Progress(true));
        instant=instant.plusSeconds(901);push.dispatch();
        assertEquals(1,sends);assertEquals(1,receipts);assertEquals("ACEITO_PROVEDOR",state());
    }
    @Test void legacyJobsRemainDeliverableAndNewJobsSurviveWorkerRestart()throws Exception {
        register();change();execute("UPDATE push_envios SET roles_afetados=NULL");push.dispatch();assertEquals(1,sends);
        execute("UPDATE roles SET horario_fim='21:00'");push.collect();
        new PushService(new SqlAccess(source),new PushGateway(){
            public Result send(String t,int id){assertEquals(token,t);assertEquals(route,id);sends++;return sendResult;}
            public Result receipt(String t){return receiptResult;}
        },clock).dispatch();
        assertEquals(2,sends);
    }
    @Test void optInDedupRepeatedChangesAndProviderReceipt()throws Exception {
        register();push.collect();assertEquals(0,count("push_envios"));change();push.collect();assertEquals(1,count("push_envios"));
        register();push.collect();assertEquals(1,count("push_envios"));
        push.dispatch();push.dispatch();assertEquals(1,sends);assertEquals("RECIBO",state());
        instant=instant.plusSeconds(901);push.dispatch();assertEquals(1,receipts);assertEquals("ACEITO_PROVEDOR",state());
        execute("UPDATE roles SET horario_fim='21:00'");push.collect();assertEquals(2,count("push_envios"));
        execute("UPDATE roles SET horario_fim=NULL");push.collect();assertEquals(3,count("push_envios"));
        assertNull(plans.find(1,route).paradas().get(0).horario());
    }
    @Test void optOutLogoutExpirationAndAccountSwitchStopDelivery()throws Exception {
        register();change();push.remove(other);assertEquals(1,count("push_envios"));push.remove(session);push.dispatch();assertEquals(0,sends);
        register();execute("UPDATE roles SET cancelado=TRUE");push.collect();
        push.register(2,other,new PushService.Registration(token));assertFalse(push.status(session).ativo());push.dispatch();assertEquals(0,sends);
        push.remove(other);register();execute("UPDATE roles SET cancelado=FALSE");push.collect();
        execute("UPDATE sessoes SET expira_em='2026-10-15 00:00:00'");push.dispatch();assertEquals(0,sends);assertEquals("CANCELADO",state());
        execute("DELETE FROM sessoes");assertEquals(0,count("push_dispositivos"));assertEquals(0,count("push_envios"));
    }
    @Test void completedDeletedAndPastRoutesDoNotSend()throws Exception {
        register();change();plans.progress(1,route,1,new ItineraryService.Progress(true));push.dispatch();assertEquals("CANCELADO",state());assertEquals(0,sends);
        plans.progress(1,route,1,new ItineraryService.Progress(false));execute("UPDATE roles SET cancelado=TRUE");push.collect();
        instant=instant.plus(Duration.ofDays(2));push.dispatch();assertEquals(0,sends);
        plans.delete(1,route);assertEquals(0,count("push_envios"));assertEquals(0,count("push_estado"));
    }
    @Test void transientFailureBacksOffAndRetriesWithoutLosingQueue()throws Exception {
        register();change();offline=true;push.dispatch();push.dispatch();assertEquals(1,sends);assertEquals("PENDENTE",state());
        instant=instant.plusSeconds(121);offline=false;push.dispatch();assertEquals(2,sends);assertEquals("RECIBO",state());
    }
    @Test void invalidDeviceReceiptRevokesSubscription()throws Exception {
        register();change();push.dispatch();receiptResult=new PushGateway.Result("error",null,"DeviceNotRegistered");
        instant=instant.plusSeconds(901);push.dispatch();assertEquals(0,count("push_dispositivos"));assertEquals(0,count("push_envios"));
    }
    @Test void permanentCredentialsFailureIsVisibleWithoutRetryLoop()throws Exception {
        register();change();sendResult=new PushGateway.Result("error",null,"InvalidCredentials");push.dispatch();push.dispatch();
        assertEquals(1,sends);assertEquals("FALHOU",state());assertEquals("InvalidCredentials",push.status(session).ultimoErro());
    }
    @Test void namesDoNotTriggerAndOldWarningsAreBaselined()throws Exception {
        execute("UPDATE roles SET horario_fim='11:00'");register();push.collect();assertEquals(0,count("push_envios"));
        execute("UPDATE roles SET nome='Outro nome'");push.collect();assertEquals(0,count("push_envios"));
        execute("DELETE FROM roles");push.collect();assertEquals(1,count("push_envios"));
    }
    @Test void refusesInvalidTokenAndWrongSessionOwner() {
        assertEquals(400,assertThrows(ApiException.class,()->push.register(1,session,new PushService.Registration("bad"))).status());
        assertEquals(401,assertThrows(ApiException.class,()->push.register(2,session,new PushService.Registration(token))).status());
    }
}
