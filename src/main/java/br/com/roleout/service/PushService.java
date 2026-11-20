package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.PushDAO;
import br.com.roleout.security.Tokens;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Durable schedule-change detection. Only published data is observed. */
public final class PushService {
    public record Registration(String expoToken) {}
    public record Status(boolean ativo, boolean envioConfigurado, String ultimoErro) {}
    private static final ObjectMapper JSON=new ObjectMapper();
    private final SqlAccess sql;
    private final PushGateway gateway;
    private final Clock clock;
    public PushService() { this(new SqlAccess(),new ExpoPushGateway(),Clock.systemUTC()); }
    public PushService(SqlAccess sql,PushGateway gateway,Clock clock) { this.sql=sql; this.gateway=gateway; this.clock=clock; }
    public static boolean enabled() { return "true".equalsIgnoreCase(System.getenv("PUSH_ENABLED")); }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private java.sql.Date today() { return java.sql.Date.valueOf(LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")))); }
    public Status status(String session) {
        try(var c=sql.connection()) {
            var status=new PushDAO(c).status(Tokens.digest(session));
            return new Status(status!=null,enabled(),status==null?null:status.error());
        } catch(SQLException e) { throw new RuntimeException("Push status failed",e); }
    }
    public void remove(String session) {
        try(var c=sql.connection()) { new PushDAO(c).removeSession(Tokens.digest(session)); }
        catch(SQLException e) { throw new RuntimeException("Push opt-out failed",e); }
    }
    public void register(int account,String session,Registration input) {
        if(input.expoToken()==null || !input.expoToken().matches("(?:ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]{10,200}\\]"))
            throw new ApiException(400,"TOKEN_INVALIDO","Dispositivo de notificações inválido.");
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                var dao=new PushDAO(c);
                String digest=Tokens.digest(session);
                Integer owner=dao.lockSession(digest,now());
                if(owner==null || owner!=account) throw new ApiException(401,"NAO_AUTENTICADO","Entre novamente na conta.");
                if(dao.registered(digest,input.expoToken())) { c.commit();return; }
                // Token rotation/account switch invalidates old queued notifications atomically.
                int device=dao.replaceDevice(digest,input.expoToken());
                observe(c,device,account,true); c.commit();
            } catch(Exception e) { c.rollback();throw e; }
        } catch(SQLException e) { throw new RuntimeException("Push registration failed",e); }
    }
    private Map<String,String> published(Connection c,int route) throws SQLException {
        var result=new TreeMap<String,String>();
        for(var r:new PushDAO(c).published(route)) result.put(String.valueOf(r.role()),
            !r.exists()?"REMOVIDO":SavedScheduleCheck.snapshot(r.date(),r.endDate(),r.time(),r.endTime(),r.hours())+"|"+r.cancelled()+"|"+r.city()+"|"+r.venueStatus()+"|"+r.organizationStatus());
        return result;
    }
    private void observe(Connection c,int device,int account,boolean baseline) throws SQLException {
        var dao=new PushDAO(c);
        for(int route:dao.routes(account,today())) {
            Map<String,String> current=published(c,route),old=null;
            String observed=dao.observed(device,route);
            if(observed!=null) try { old=JSON.readValue(observed,new TypeReference<Map<String,String>>(){}); }
            catch(java.io.IOException e) { throw new SQLException("Invalid observed schedule",e); }
            var changed=new TreeSet<Integer>();
            if(!baseline) {
                if(old!=null) { for(var entry:current.entrySet()) if(old.containsKey(entry.getKey()) && !old.get(entry.getKey()).equals(entry.getValue())) changed.add(Integer.parseInt(entry.getKey())); }
                else SavedScheduleCheck.check(c,account,route).alertas().stream().filter(a->!a.codigo().equals("SEM_HISTORICO")).forEach(a->changed.add(a.roleId()));
            }
            try {
                if(!changed.isEmpty()) dao.enqueue(device,route,now(),JSON.writeValueAsString(changed));
                dao.observe(device,route,JSON.writeValueAsString(current),old==null);
            } catch(java.io.IOException e) { throw new SQLException(e); }
        }
    }
    public void collect() throws SQLException {
        List<Integer> devices;
        try(var c=sql.connection()) { devices=new PushDAO(c).devices(); }
        for(int device:devices) try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                Integer account=new PushDAO(c).lockDevice(device,now());
                if(account!=null) observe(c,device,account,false);
                c.commit();
            } catch(Exception e) { c.rollback();throw e; }
        }
    }
    public void dispatch() throws SQLException {
        List<Integer> ids;
        try(var c=sql.connection()) { ids=new PushDAO(c).due(now()); }
        for(int id:ids) deliver(id);
        try(var c=sql.connection()) { new PushDAO(c).prune(Timestamp.from(clock.instant().minus(Duration.ofDays(30)))); }
    }
    private boolean hasAffectedPendingStop(Connection c,int route,String affected) throws SQLException {
        // Older queued jobs cannot be attributed to individual stops; preserve their existing behavior.
        if(affected==null) return true;
        Set<Integer> ids;
        try { ids=JSON.readValue(affected,new TypeReference<Set<Integer>>(){}); }
        catch(java.io.IOException e) { throw new SQLException("Invalid affected stops",e); }
        if(ids==null) throw new SQLException("Missing affected stops");
        return new PushDAO(c).lockPendingStops(route).stream().anyMatch(ids::contains);
    }
    private void deliver(int id) throws SQLException {
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                // Row lock prevents two application workers from submitting the same pending job.
                var dao=new PushDAO(c);
                var r=dao.lockJob(id,now());
                if(r!=null) {
                    int attempts=r.attempts()+1;
                    boolean receipt=r.state().equals("RECIBO");
                    String state="PENDENTE",error=null,ticket=r.ticket();
                    long delay=Math.min(3600,60L*(1L<<Math.min(attempts,6)));
                    if(!r.expires().after(now()) || r.date().before(today()) || !r.pending()
                        || (!receipt && !hasAffectedPendingStop(c,r.route(),r.affected()))) state="CANCELADO";
                    else {
                        PushGateway.Result result;
                        try { result=receipt?gateway.receipt(ticket):gateway.send(r.token(),r.route()); }
                        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new SQLException("Push interrupted",e); }
                        catch(Exception e) { result=new PushGateway.Result("retry",null,"REDE"); }
                        if(result.status().equals("ok")) { state=receipt?"ACEITO_PROVEDOR":"RECIBO"; if(!receipt) {ticket=result.ticket();attempts=0;delay=900;} }
                        else if("DeviceNotRegistered".equals(result.error())) {
                            dao.removeDevice(r.device());
                            c.commit();return;
                        } else {
                            error=result.error();state=receipt?"RECIBO":"PENDENTE";
                            if(result.status().equals("error") && !"MessageRateExceeded".equals(error)) state="FALHOU";
                            // A failed receipt may be retried only when the provider confirms non-delivery.
                            if(receipt && "MessageRateExceeded".equals(error)) { state="PENDENTE";ticket=null; }
                            if(attempts>=6) { state="FALHOU";if(error==null) error="SEM_RECIBO"; }
                        }
                    }
                    dao.updateJob(id,state,attempts,Timestamp.from(clock.instant().plusSeconds(delay)),ticket,error);
                }
                c.commit();
            } catch(Exception e) { c.rollback();throw e; }
        }
    }
}
