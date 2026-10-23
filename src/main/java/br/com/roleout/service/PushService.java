package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
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
        try(var c=sql.connection();var s=c.prepareStatement("SELECT d.id,(SELECT e.erro FROM push_envios e WHERE e.dispositivo_id=d.id AND e.erro IS NOT NULL ORDER BY e.id DESC LIMIT 1) AS erro FROM push_dispositivos d WHERE sessao_hash=?")) {
            s.setString(1,Tokens.digest(session));
            try(var r=s.executeQuery()) { return r.next()?new Status(true,enabled(),r.getString("erro")):new Status(false,enabled(),null); }
        } catch(SQLException e) { throw new RuntimeException("Push status failed",e); }
    }
    public void remove(String session) {
        try(var c=sql.connection();var s=c.prepareStatement("DELETE FROM push_dispositivos WHERE sessao_hash=?")) {
            s.setString(1,Tokens.digest(session));s.executeUpdate();
        } catch(SQLException e) { throw new RuntimeException("Push opt-out failed",e); }
    }
    public void register(int account,String session,Registration input) {
        if(input.expoToken()==null || !input.expoToken().matches("(?:ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]{10,200}\\]"))
            throw new ApiException(400,"TOKEN_INVALIDO","Dispositivo de notificações inválido.");
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                // Bind to the authenticated session; expiration/logout also revokes delivery.
                try(var s=c.prepareStatement("SELECT conta_id FROM sessoes WHERE token_hash=? AND expira_em>? FOR UPDATE")) {
                    s.setString(1,Tokens.digest(session));s.setTimestamp(2,now());
                    try(var r=s.executeQuery()) { if(!r.next() || r.getInt(1)!=account) throw new ApiException(401,"NAO_AUTENTICADO","Entre novamente na conta."); }
                }
                try(var s=c.prepareStatement("SELECT id FROM push_dispositivos WHERE sessao_hash=? AND expo_token=?")) {
                    s.setString(1,Tokens.digest(session));s.setString(2,input.expoToken());
                    try(var r=s.executeQuery()) { if(r.next()) { c.commit();return; } }
                }
                // Token rotation/account switch invalidates old queued notifications atomically.
                try(var s=c.prepareStatement("DELETE FROM push_dispositivos WHERE sessao_hash=? OR expo_token=?")) {
                    s.setString(1,Tokens.digest(session));s.setString(2,input.expoToken());s.executeUpdate();
                }
                int device;
                try(var s=c.prepareStatement("INSERT INTO push_dispositivos(sessao_hash,expo_token) VALUES (?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1,Tokens.digest(session));s.setString(2,input.expoToken());s.executeUpdate();
                    try(var r=s.getGeneratedKeys()) { r.next();device=r.getInt(1); }
                }
                observe(c,device,account,true); c.commit();
            } catch(Exception e) { c.rollback();throw e; }
        } catch(SQLException e) { throw new RuntimeException("Push registration failed",e); }
    }
    private Map<String,String> published(Connection c,int route) throws SQLException {
        var result=new TreeMap<String,String>();
        try(var s=c.prepareStatement("""
            SELECT p.role_id,r.*,l.status AS local_status,o.status AS org_status
            FROM roteiro_paradas p LEFT JOIN roles r ON r.id=p.role_id
            LEFT JOIN locais l ON l.id=r.local_id LEFT JOIN organizacoes o ON o.id=l.organizacao_id
            WHERE p.roteiro_id=? AND p.concluida=FALSE ORDER BY p.ordem
            """)) {
            s.setInt(1,route);
            try(var r=s.executeQuery()) { while(r.next()) result.put(String.valueOf(r.getInt("role_id")),
                r.getObject("id")==null?"REMOVIDO":SavedScheduleCheck.snapshot(r)+"|"+r.getBoolean("cancelado")+"|"+r.getString("cidade")+"|"+r.getString("local_status")+"|"+r.getString("org_status")); }
        }
        return result;
    }
    private void observe(Connection c,int device,int account,boolean baseline) throws SQLException {
        var routes=new ArrayList<Integer>();
        try(var s=c.prepareStatement("SELECT id FROM roteiros WHERE conta_id=? AND data_planejada>=? AND EXISTS(SELECT 1 FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id AND p.concluida=FALSE)")) {
            s.setInt(1,account);s.setDate(2,today());try(var r=s.executeQuery()) { while(r.next()) routes.add(r.getInt(1)); }
        }
        for(int route:routes) {
            Map<String,String> current=published(c,route),old=null;
            try(var s=c.prepareStatement("SELECT observado FROM push_estado WHERE dispositivo_id=? AND roteiro_id=?")) {
                s.setInt(1,device);s.setInt(2,route);
                try(var r=s.executeQuery()) { if(r.next()) try { old=JSON.readValue(r.getString(1),new TypeReference<Map<String,String>>(){}); } catch(java.io.IOException e) { throw new SQLException("Invalid observed schedule",e); } }
            }
            var changed=new TreeSet<Integer>();
            if(!baseline) {
                if(old!=null) { for(var entry:current.entrySet()) if(old.containsKey(entry.getKey()) && !old.get(entry.getKey()).equals(entry.getValue())) changed.add(Integer.parseInt(entry.getKey())); }
                else SavedScheduleCheck.check(c,account,route).alertas().stream().filter(a->!a.codigo().equals("SEM_HISTORICO")).forEach(a->changed.add(a.roleId()));
            }
            if(!changed.isEmpty()) try(var s=c.prepareStatement("INSERT INTO push_envios(dispositivo_id,roteiro_id,proxima_tentativa,roles_afetados) VALUES (?,?,?,?)")) {
                s.setInt(1,device);s.setInt(2,route);s.setTimestamp(3,now());
                try { s.setString(4,JSON.writeValueAsString(changed)); } catch(java.io.IOException e) { throw new SQLException(e); }
                s.executeUpdate();
            }
            try(var s=c.prepareStatement(old==null?"INSERT INTO push_estado(observado,dispositivo_id,roteiro_id) VALUES (?,?,?)":"UPDATE push_estado SET observado=? WHERE dispositivo_id=? AND roteiro_id=?")) {
                try { s.setString(1,JSON.writeValueAsString(current)); } catch(java.io.IOException e) { throw new SQLException(e); }
                s.setInt(2,device);s.setInt(3,route);s.executeUpdate();
            }
        }
    }
    public void collect() throws SQLException {
        var devices=new ArrayList<Integer>();
        try(var c=sql.connection();var s=c.createStatement();var r=s.executeQuery("SELECT id FROM push_dispositivos")) { while(r.next()) devices.add(r.getInt(1)); }
        for(int device:devices) try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.prepareStatement("SELECT se.conta_id FROM push_dispositivos d JOIN sessoes se ON se.token_hash=d.sessao_hash WHERE d.id=? AND se.expira_em>? FOR UPDATE")) {
                    s.setInt(1,device);s.setTimestamp(2,now());
                    try(var r=s.executeQuery()) { if(r.next()) observe(c,device,r.getInt(1),false); }
                }
                c.commit();
            } catch(Exception e) { c.rollback();throw e; }
        }
    }
    public void dispatch() throws SQLException {
        var ids=new ArrayList<Integer>();
        try(var c=sql.connection();var s=c.prepareStatement("SELECT id FROM push_envios WHERE estado IN ('PENDENTE','RECIBO') AND proxima_tentativa<=? ORDER BY id LIMIT 20")) {
            s.setTimestamp(1,now());try(var r=s.executeQuery()) { while(r.next()) ids.add(r.getInt(1)); }
        }
        for(int id:ids) deliver(id);
        try(var c=sql.connection();var s=c.prepareStatement("DELETE FROM push_envios WHERE criado_em<?")) {
            s.setTimestamp(1,Timestamp.from(clock.instant().minus(Duration.ofDays(30))));s.executeUpdate();
        }
    }
    private boolean hasAffectedPendingStop(Connection c,int route,String affected) throws SQLException {
        // Older queued jobs cannot be attributed to individual stops; preserve their existing behavior.
        if(affected==null) return true;
        Set<Integer> ids;
        try { ids=JSON.readValue(affected,new TypeReference<Set<Integer>>(){}); }
        catch(java.io.IOException e) { throw new SQLException("Invalid affected stops",e); }
        if(ids==null) throw new SQLException("Missing affected stops");
        try(var s=c.prepareStatement("SELECT role_id FROM roteiro_paradas WHERE roteiro_id=? AND concluida=FALSE FOR UPDATE")) {
            s.setInt(1,route);
            try(var r=s.executeQuery()) { while(r.next()) if(ids.contains(r.getInt(1))) return true; }
        }
        return false;
    }
    private void deliver(int id) throws SQLException {
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                // Row lock prevents two application workers from submitting the same pending job.
                try(var s=c.prepareStatement("""
                    SELECT e.*,d.expo_token,se.expira_em,r.data_planejada,
                    EXISTS(SELECT 1 FROM roteiro_paradas p WHERE p.roteiro_id=r.id AND p.concluida=FALSE) AS pendente
                    FROM push_envios e JOIN push_dispositivos d ON d.id=e.dispositivo_id
                    JOIN sessoes se ON se.token_hash=d.sessao_hash JOIN roteiros r ON r.id=e.roteiro_id
                    WHERE e.id=? AND e.estado IN ('PENDENTE','RECIBO') AND e.proxima_tentativa<=? FOR UPDATE
                    """)) {
                    s.setInt(1,id);s.setTimestamp(2,now());
                    try(var r=s.executeQuery()) {
                        if(r.next()) {
                            int attempts=r.getInt("tentativas")+1;
                            boolean receipt=r.getString("estado").equals("RECIBO");
                            String state="PENDENTE",error=null,ticket=r.getString("ticket");
                            long delay=Math.min(3600,60L*(1L<<Math.min(attempts,6)));
                            if(!r.getTimestamp("expira_em").after(now()) || r.getDate("data_planejada").before(today()) || !r.getBoolean("pendente")
                                || (!receipt && !hasAffectedPendingStop(c,r.getInt("roteiro_id"),r.getString("roles_afetados")))) state="CANCELADO";
                            else {
                                PushGateway.Result result;
                                try { result=receipt?gateway.receipt(ticket):gateway.send(r.getString("expo_token"),r.getInt("roteiro_id")); }
                                catch(InterruptedException e) { Thread.currentThread().interrupt();throw new SQLException("Push interrupted",e); }
                                catch(Exception e) { result=new PushGateway.Result("retry",null,"REDE"); }
                                if(result.status().equals("ok")) { state=receipt?"ACEITO_PROVEDOR":"RECIBO"; if(!receipt) {ticket=result.ticket();attempts=0;delay=900;} }
                                else if("DeviceNotRegistered".equals(result.error())) {
                                    try(var remove=c.prepareStatement("DELETE FROM push_dispositivos WHERE id=?")) { remove.setInt(1,r.getInt("dispositivo_id"));remove.executeUpdate(); }
                                    c.commit();return;
                                } else {
                                    error=result.error();state=receipt?"RECIBO":"PENDENTE";
                                    if(result.status().equals("error") && !"MessageRateExceeded".equals(error)) state="FALHOU";
                                    // A failed receipt may be retried only when the provider confirms non-delivery.
                                    if(receipt && "MessageRateExceeded".equals(error)) { state="PENDENTE";ticket=null; }
                                    if(attempts>=6) { state="FALHOU";if(error==null) error="SEM_RECIBO"; }
                                }
                            }
                            try(var update=c.prepareStatement("UPDATE push_envios SET estado=?,tentativas=?,proxima_tentativa=?,ticket=?,erro=? WHERE id=?")) {
                                update.setString(1,state);update.setInt(2,attempts);update.setTimestamp(3,Timestamp.from(clock.instant().plusSeconds(delay)));update.setString(4,ticket);update.setString(5,error);update.setInt(6,id);update.executeUpdate();
                            }
                        }
                    }
                }
                c.commit();
            } catch(Exception e) { c.rollback();throw e; }
        }
    }
}
