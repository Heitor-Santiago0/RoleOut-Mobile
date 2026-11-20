package br.com.roleout.dao;
import br.com.roleout.model.DiscoveryData.Settings;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public final class DiscoveryDAO {
    private final Connection c;
    public DiscoveryDAO(Connection c){this.c=c;}
    public boolean lockAccount(int account)throws SQLException{return new ItineraryDAO(c).lockAccount(account);}
    public Settings settings(int account)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM sugestoes_config WHERE conta_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?new Settings(r.getBoolean("ativo"),r.getBoolean("viagens"),r.getInt("intervalo_horas"),r.getString("cidade_base")):null;}}
    }
    public void save(int account,Settings value)throws SQLException {
        boolean exists=settings(account)!=null;
        try(var s=c.prepareStatement(exists?"UPDATE sugestoes_config SET ativo=?,viagens=?,intervalo_horas=?,cidade_base=? WHERE conta_id=?":"INSERT INTO sugestoes_config(ativo,viagens,intervalo_horas,cidade_base,conta_id) VALUES (?,?,?,?,?)")){
            s.setBoolean(1,value.ativo());s.setBoolean(2,value.viagens());s.setInt(3,value.intervaloHoras());s.setString(4,value.cidadeBase());s.setInt(5,account);s.executeUpdate();
        }
    }
    public Instant lastNotice(int account)throws SQLException {
        try(var s=c.prepareStatement("SELECT ultimo_aviso FROM sugestoes_config WHERE conta_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){var t=r.next()?r.getTimestamp(1):null;return t==null?null:t.toInstant();}}
    }
    public Set<Integer> sentSince(int account,Instant after)throws SQLException {
        try(var s=c.prepareStatement("SELECT role_id FROM sugestoes_entregas WHERE conta_id=? AND enviado_em>?")){s.setInt(1,account);s.setTimestamp(2,Timestamp.from(after));var sent=new HashSet<Integer>();try(var r=s.executeQuery()){while(r.next())sent.add(r.getInt(1));}return sent;}
    }
    public void recordDelivery(int account,int role,Instant now,Instant cutoff)throws SQLException {
        try(var s=c.prepareStatement("UPDATE sugestoes_config SET ultimo_aviso=? WHERE conta_id=?")){s.setTimestamp(1,Timestamp.from(now));s.setInt(2,account);s.executeUpdate();}
        try(var s=c.prepareStatement("DELETE FROM sugestoes_entregas WHERE conta_id=? AND enviado_em<=?")){s.setInt(1,account);s.setTimestamp(2,Timestamp.from(cutoff));s.executeUpdate();}
        try(var s=c.prepareStatement("INSERT INTO sugestoes_entregas(conta_id,role_id,enviado_em) VALUES (?,?,?)")){s.setInt(1,account);s.setInt(2,role);s.setTimestamp(3,Timestamp.from(now));s.executeUpdate();}
    }
}
