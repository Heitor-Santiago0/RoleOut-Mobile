package br.com.roleout.service;
import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.model.PasswordRecoveryData.*;
import br.com.roleout.security.Tokens;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordRecoveryTest {
    JdbcDataSource source;AuthService auth;PasswordRecoveryService service;Sink mail;MutableClock clock;int account;
    static class MutableClock extends Clock {Instant now=Instant.parse("2026-11-26T15:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}void advance(long seconds){now=now.plusSeconds(seconds);}}
    static class Sink implements RecoveryMail {boolean active=true,fail;int attempts;String code,email;public boolean enabled(){return active;}public void send(String email,String code)throws Exception{attempts++;if(fail)throw new IOException("simulated");this.email=email;this.code=code;}}
    @BeforeEach void setup()throws Exception {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection()){
            for(String file:List.of("V001__roles.sql","V002__organizacoes_e_locais.sql","V003__sessoes.sql","V031__password_recovery.sql"))try(var r=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/"+file)),StandardCharsets.UTF_8)){RunScript.execute(c,r);}
        }
        var sql=new SqlAccess(source);auth=new AuthService(sql);mail=new Sink();clock=new MutableClock();service=new PasswordRecoveryService(sql,mail,clock);
        account=auth.signup(new AuthService.Signup("Teste","recovery@example.com","abc123")).id();
    }
    void ask(){service.request(new Request("recovery@example.com"));}
    @Test void validCodeChangesPasswordOnceAndRevokesSessions()throws Exception {
        String session=auth.login(new AuthService.Login("recovery@example.com","abc123")).token();
        ask();assertNull(mail.code);assertEquals(1,service.dispatch());assertEquals("recovery@example.com",mail.email);
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT token_hash FROM recuperacoes_senha")){r.next();assertEquals(Tokens.digest(mail.code),r.getString(1));assertNotEquals(mail.code,r.getString(1));}
        service.reset(new Reset(mail.code,"nov123"));
        assertThrows(ApiException.class,()->auth.authenticate(session));assertThrows(ApiException.class,()->auth.login(new AuthService.Login("recovery@example.com","abc123")));
        assertNotNull(auth.login(new AuthService.Login("recovery@example.com","nov123")));
        assertEquals(400,assertThrows(ApiException.class,()->service.reset(new Reset(mail.code,"outra123"))).status());
    }
    @Test void unknownEmailDoesNotQueueOrSend() {
        service.request(new Request("unknown@example.com"));assertEquals(0,service.dispatch());assertNull(mail.code);
    }
    @Test void expiryAndMalformedCodeNeverChangePassword(){
        ask();service.dispatch();clock.advance(900);
        assertThrows(ApiException.class,()->service.reset(new Reset(mail.code,"nov123")));
        assertThrows(ApiException.class,()->service.reset(new Reset("bad","nov123")));
        assertNotNull(auth.login(new AuthService.Login("recovery@example.com","abc123")));
    }
    @Test void newRequestInvalidatesPreviousCodeAndHasCooldown(){
        ask();service.dispatch();String first=mail.code;
        ask();assertEquals(0,service.dispatch());assertEquals(1,mail.attempts);
        clock.advance(61);ask();assertThrows(ApiException.class,()->service.reset(new Reset(first,"nov123")));
        service.dispatch();assertNotEquals(first,mail.code);
        clock.advance(61);ask();service.dispatch();clock.advance(61);ask();assertEquals(0,service.dispatch());assertEquals(3,mail.attempts);
        clock.advance(3600);ask();assertEquals(1,service.dispatch());
    }
    @Test void queueSurvivesWorkerRecreationAndRetriesBoundedly(){
        mail.fail=true;ask();assertEquals(0,service.dispatch());assertEquals(1,mail.attempts);assertEquals(0,service.dispatch());
        service=new PasswordRecoveryService(new SqlAccess(source),mail,clock);clock.advance(121);service.dispatch();clock.advance(121);service.dispatch();clock.advance(121);service.dispatch();assertEquals(3,mail.attempts);
    }
    @Test void retryAfterSmtpFailureCanDeliver(){
        mail.fail=true;ask();service.dispatch();clock.advance(121);mail.fail=false;assertEquals(1,service.dispatch());assertEquals(0,service.dispatch());service.reset(new Reset(mail.code,"nov123"));
    }
    @Test void invalidPasswordDoesNotConsumeCode(){
        ask();service.dispatch();assertThrows(IllegalArgumentException.class,()->service.reset(new Reset(mail.code,"short")));service.reset(new Reset(mail.code,"abc456"));
    }
    @Test void disabledMailIsExplicitForKnownAndUnknownAccounts(){
        mail.active=false;assertEquals(503,assertThrows(ApiException.class,this::ask).status());assertEquals(503,assertThrows(ApiException.class,()->service.request(new Request("unknown@example.com"))).status());assertEquals(0,service.dispatch());
    }
    @Test void concurrentConsumptionHasOnlyOneWinner()throws Exception {
        ask();service.dispatch();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var futures=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<2;i++)futures.add(pool.submit(()->{try{service.reset(new Reset(mail.code,"abc456"));return true;}catch(ApiException e){return false;}}));
            int wins=0;for(var f:futures)if(f.get())wins++;assertEquals(1,wins);
        }finally{pool.shutdownNow();}
    }
    @Test void smtpRequiresTlsExceptExplicitLocalSink(){
        var env=new HashMap<String,String>();env.put("MAIL_HOST","smtp.example.com");env.put("MAIL_FROM","roleout@example.com");env.put("MAIL_USER","test");env.put("MAIL_PASSWORD","fixture");
        var smtp=new SmtpRecoveryMail(env);assertTrue(smtp.enabled());assertEquals("true",smtp.properties().getProperty("mail.smtp.starttls.required"));assertEquals("true",smtp.properties().getProperty("mail.smtp.ssl.checkserveridentity"));
        env.put("MAIL_TLS","none");assertFalse(new SmtpRecoveryMail(env).enabled());
        env.put("MAIL_HOST","mailpit");env.put("MAIL_LOCAL_TEST","true");assertTrue(new SmtpRecoveryMail(env).enabled());
        env.put("ROLEOUT_PUBLIC_DEPLOYMENT","true");assertFalse(new SmtpRecoveryMail(env).enabled());
    }
}
