package br.com.roleout.dao;
import java.sql.*;
import java.util.*;

/** Notification persistence. Delivery policy, retries and gateway calls stay in the service. */
public final class PushDAO {
    public record DeviceStatus(String error) {}
    public record PublishedStop(int role,boolean exists,String date,String endDate,String time,String endTime,String hours,
        boolean cancelled,String city,String venueStatus,String organizationStatus) {}
    public record Job(int attempts,String state,String ticket,Timestamp expires,java.sql.Date date,boolean pending,
        int route,String affected,String token,int device) {}
    private final Connection c;
    public PushDAO(Connection c){this.c=c;}
    public DeviceStatus status(String digest)throws SQLException {
        try(var s=c.prepareStatement("SELECT d.id,(SELECT e.erro FROM push_envios e WHERE e.dispositivo_id=d.id AND e.erro IS NOT NULL ORDER BY e.id DESC LIMIT 1) AS erro FROM push_dispositivos d WHERE sessao_hash=?")){s.setString(1,digest);try(var r=s.executeQuery()){return r.next()?new DeviceStatus(r.getString("erro")):null;}}
    }
    public void removeSession(String digest)throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM push_dispositivos WHERE sessao_hash=?")){s.setString(1,digest);s.executeUpdate();}
    }
    public Integer lockSession(String digest,Timestamp now)throws SQLException {
        try(var s=c.prepareStatement("SELECT conta_id FROM sessoes WHERE token_hash=? AND expira_em>? FOR UPDATE")){s.setString(1,digest);s.setTimestamp(2,now);try(var r=s.executeQuery()){return r.next()?r.getInt(1):null;}}
    }
    public boolean registered(String digest,String token)throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM push_dispositivos WHERE sessao_hash=? AND expo_token=?")){s.setString(1,digest);s.setString(2,token);try(var r=s.executeQuery()){return r.next();}}
    }
    public int replaceDevice(String digest,String token)throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM push_dispositivos WHERE sessao_hash=? OR expo_token=?")){s.setString(1,digest);s.setString(2,token);s.executeUpdate();}
        try(var s=c.prepareStatement("INSERT INTO push_dispositivos(sessao_hash,expo_token) VALUES (?,?)",Statement.RETURN_GENERATED_KEYS)){s.setString(1,digest);s.setString(2,token);s.executeUpdate();try(var r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Missing device ID");return r.getInt(1);}}
    }
    public List<PublishedStop> published(int route)throws SQLException {
        var items=new ArrayList<PublishedStop>();
        try(var s=c.prepareStatement("""
            SELECT p.role_id,r.*,l.status AS local_status,o.status AS org_status
            FROM roteiro_paradas p LEFT JOIN roles r ON r.id=p.role_id
            LEFT JOIN locais l ON l.id=r.local_id LEFT JOIN organizacoes o ON o.id=l.organizacao_id
            WHERE p.roteiro_id=? AND p.concluida=FALSE ORDER BY p.ordem
            """)) {
            s.setInt(1,route);try(var r=s.executeQuery()){while(r.next())items.add(new PublishedStop(r.getInt("role_id"),r.getObject("id")!=null,r.getString("data"),r.getString("data_fim"),r.getString("horario"),r.getString("horario_fim"),r.getString("funcionamento"),r.getBoolean("cancelado"),r.getString("cidade"),r.getString("local_status"),r.getString("org_status")));}
        }return items;
    }
    public List<Integer> routes(int account,java.sql.Date today)throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM roteiros WHERE conta_id=? AND data_planejada>=? AND EXISTS(SELECT 1 FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id AND p.concluida=FALSE)")){s.setInt(1,account);s.setDate(2,today);return ids(s);}
    }
    public String observed(int device,int route)throws SQLException {
        try(var s=c.prepareStatement("SELECT observado FROM push_estado WHERE dispositivo_id=? AND roteiro_id=?")){s.setInt(1,device);s.setInt(2,route);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}}
    }
    public void enqueue(int device,int route,Timestamp now,String changed)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO push_envios(dispositivo_id,roteiro_id,proxima_tentativa,roles_afetados) VALUES (?,?,?,?)")){s.setInt(1,device);s.setInt(2,route);s.setTimestamp(3,now);s.setString(4,changed);s.executeUpdate();}
    }
    public void observe(int device,int route,String content,boolean insert)throws SQLException {
        try(var s=c.prepareStatement(insert?"INSERT INTO push_estado(observado,dispositivo_id,roteiro_id) VALUES (?,?,?)":"UPDATE push_estado SET observado=? WHERE dispositivo_id=? AND roteiro_id=?")){s.setString(1,content);s.setInt(2,device);s.setInt(3,route);s.executeUpdate();}
    }
    public List<Integer> devices()throws SQLException {try(var s=c.prepareStatement("SELECT id FROM push_dispositivos")){return ids(s);}}
    public Integer lockDevice(int device,Timestamp now)throws SQLException {
        try(var s=c.prepareStatement("SELECT se.conta_id FROM push_dispositivos d JOIN sessoes se ON se.token_hash=d.sessao_hash WHERE d.id=? AND se.expira_em>? FOR UPDATE")){s.setInt(1,device);s.setTimestamp(2,now);try(var r=s.executeQuery()){return r.next()?r.getInt(1):null;}}
    }
    public List<Integer> due(Timestamp now)throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM push_envios WHERE estado IN ('PENDENTE','RECIBO') AND proxima_tentativa<=? ORDER BY id LIMIT 20")){s.setTimestamp(1,now);return ids(s);}
    }
    public void prune(Timestamp cutoff)throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM push_envios WHERE criado_em<?")){s.setTimestamp(1,cutoff);s.executeUpdate();}
    }
    public List<Integer> lockPendingStops(int route)throws SQLException {
        try(var s=c.prepareStatement("SELECT role_id FROM roteiro_paradas WHERE roteiro_id=? AND concluida=FALSE FOR UPDATE")){s.setInt(1,route);return ids(s);}
    }
    public Job lockJob(int id,Timestamp now)throws SQLException {
        try(var s=c.prepareStatement("""
            SELECT e.*,d.expo_token,se.expira_em,r.data_planejada,
            EXISTS(SELECT 1 FROM roteiro_paradas p WHERE p.roteiro_id=r.id AND p.concluida=FALSE) AS pendente
            FROM push_envios e JOIN push_dispositivos d ON d.id=e.dispositivo_id
            JOIN sessoes se ON se.token_hash=d.sessao_hash JOIN roteiros r ON r.id=e.roteiro_id
            WHERE e.id=? AND e.estado IN ('PENDENTE','RECIBO') AND e.proxima_tentativa<=? FOR UPDATE
            """)) {
            s.setInt(1,id);s.setTimestamp(2,now);try(var r=s.executeQuery()){return r.next()?new Job(r.getInt("tentativas"),r.getString("estado"),r.getString("ticket"),r.getTimestamp("expira_em"),r.getDate("data_planejada"),r.getBoolean("pendente"),r.getInt("roteiro_id"),r.getString("roles_afetados"),r.getString("expo_token"),r.getInt("dispositivo_id")):null;}
        }
    }
    public void removeDevice(int id)throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM push_dispositivos WHERE id=?")){s.setInt(1,id);s.executeUpdate();}
    }
    public void updateJob(int id,String state,int attempts,Timestamp next,String ticket,String error)throws SQLException {
        try(var s=c.prepareStatement("UPDATE push_envios SET estado=?,tentativas=?,proxima_tentativa=?,ticket=?,erro=? WHERE id=?")){s.setString(1,state);s.setInt(2,attempts);s.setTimestamp(3,next);s.setString(4,ticket);s.setString(5,error);s.setInt(6,id);s.executeUpdate();}
    }
    private List<Integer> ids(PreparedStatement s)throws SQLException {
        var ids=new ArrayList<Integer>();try(var r=s.executeQuery()){while(r.next())ids.add(r.getInt(1));}return ids;
    }
}
