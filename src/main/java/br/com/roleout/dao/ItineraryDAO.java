package br.com.roleout.dao;

import br.com.roleout.model.ItineraryData.*;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

/** Persistence only; the caller owns the transaction and all business decisions. */
public final class ItineraryDAO {
    public record CatalogStop(int id,String nome,String local,String cidade,LocalDate data,LocalDate dataFim,
            BigDecimal valor,String horario,String horarioFim,String funcionamento) {}
    public record RouteRef(int id,String nome) {}
    private static final String SUMMARY = """
        SELECT roteiros.*,
          (SELECT COUNT(*) FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id) AS total_paradas,
          (SELECT COUNT(*) FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id AND p.concluida=TRUE) AS paradas_concluidas
        FROM roteiros
        """;
    private final Connection c;
    public ItineraryDAO(Connection c){this.c=c;}
    public boolean lockAccount(int account) throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM contas WHERE id=? FOR UPDATE")){s.setInt(1,account);try(var r=s.executeQuery()){return r.next();}}
    }
    public int count(int account) throws SQLException {
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM roteiros WHERE conta_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){r.next();return r.getInt(1);}}
    }
    public CatalogStop availableStop(int id) throws SQLException {
        String query="""
            SELECT r.* FROM roles r WHERE r.id=? AND r.cancelado=FALSE AND
            (r.local_id IS NULL OR EXISTS (SELECT 1 FROM locais l JOIN organizacoes o ON o.id=l.organizacao_id
            WHERE l.id=r.local_id AND l.status='PUBLICADO' AND o.status='APROVADA'))
            """;
        try(var s=c.prepareStatement(query)){s.setInt(1,id);try(var r=s.executeQuery()){
            if(!r.next())return null;
            return new CatalogStop(id,r.getString("nome"),r.getString("local"),r.getString("cidade"),date(r,"data"),date(r,"data_fim"),r.getBigDecimal("valor"),r.getString("horario"),r.getString("horario_fim"),r.getString("funcionamento"));
        }}
    }
    static LocalDate date(ResultSet r,String key) throws SQLException {var d=r.getDate(key);return d==null?null:d.toLocalDate();}
    public int insert(int account,String name,String city,LocalDate date,Input input,long budget,long total) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO roteiros (conta_id,nome,cidade,data_planejada,partida,duracao_minutos,pessoas,orcamento_centavos,modo_orcamento,total_centavos) VALUES (?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            s.setInt(1,account);s.setString(2,name);s.setString(3,city);s.setDate(4,java.sql.Date.valueOf(date));s.setString(5,input.partida());s.setInt(6,input.duracaoMinutos());s.setInt(7,input.pessoas());s.setLong(8,budget);s.setString(9,input.modoOrcamento());s.setLong(10,total);s.executeUpdate();
            try(var r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Missing itinerary key");return r.getInt(1);}
        }
    }
    public void insertStops(int id,List<Stop> stops,Map<Integer,String> snapshots) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO roteiro_paradas (roteiro_id,ordem,role_id,nome,local_nome,minutos,preco_centavos,horario,horarios_originais) VALUES (?,?,?,?,?,?,?,?,?)")) {
            for(int i=0;i<stops.size();i++){var p=stops.get(i);s.setInt(1,id);s.setInt(2,i);s.setInt(3,p.roleId());s.setString(4,p.nome());s.setString(5,p.local());s.setInt(6,p.minutos());s.setLong(7,p.precoCentavos());s.setString(8,p.horario());s.setString(9,snapshots.get(p.roleId()));s.addBatch();}s.executeBatch();
        }
    }
    public List<RouteRef> pendingRoutes(int account) throws SQLException {
        try(var s=c.prepareStatement("SELECT id,nome FROM roteiros WHERE conta_id=? AND EXISTS(SELECT 1 FROM roteiro_paradas p WHERE p.roteiro_id=roteiros.id AND p.concluida=FALSE) ORDER BY data_planejada,id LIMIT 100")) {
            s.setInt(1,account);var items=new ArrayList<RouteRef>();try(var r=s.executeQuery()){while(r.next())items.add(new RouteRef(r.getInt(1),r.getString(2)));}return items;
        }
    }
    public List<Summary> list(int account,int offset) throws SQLException {
        try(var s=c.prepareStatement(SUMMARY+" WHERE conta_id=? ORDER BY id DESC LIMIT 20 OFFSET ?")) {
            s.setInt(1,account);s.setInt(2,offset);var items=new ArrayList<Summary>();try(var r=s.executeQuery()){while(r.next())items.add(summary(r));}return items;
        }
    }
    public boolean lockOwned(int account,int id) throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM roteiros WHERE conta_id=? AND id=? FOR UPDATE")){s.setInt(1,account);s.setInt(2,id);try(var r=s.executeQuery()){return r.next();}}
    }
    public void rename(int account,int id,String name) throws SQLException {
        try(var s=c.prepareStatement("UPDATE roteiros SET nome=? WHERE conta_id=? AND id=?")){s.setString(1,name);s.setInt(2,account);s.setInt(3,id);s.executeUpdate();}
    }
    public boolean delete(int account,int id) throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM roteiros WHERE conta_id=? AND id=?")){s.setInt(1,account);s.setInt(2,id);return s.executeUpdate()==1;}
    }
    public boolean hasStop(int id,int roleId) throws SQLException {
        try(var s=c.prepareStatement("SELECT role_id FROM roteiro_paradas WHERE roteiro_id=? AND role_id=?")){s.setInt(1,id);s.setInt(2,roleId);try(var r=s.executeQuery()){return r.next();}}
    }
    public void progress(int id,int roleId,boolean done) throws SQLException {
        try(var s=c.prepareStatement("UPDATE roteiro_paradas SET concluida=? WHERE roteiro_id=? AND role_id=?")){s.setBoolean(1,done);s.setInt(2,id);s.setInt(3,roleId);s.executeUpdate();}
    }
    public Detail find(int account,int id) throws SQLException {
        Summary header;
        try(var s=c.prepareStatement(SUMMARY+" WHERE conta_id=? AND id=?")){s.setInt(1,account);s.setInt(2,id);try(var r=s.executeQuery()){if(!r.next())return null;header=summary(r);}}
        var stops=new ArrayList<Stop>();
        try(var s=c.prepareStatement("SELECT p.*, COALESCE(r.cancelado,FALSE) AS cancelado FROM roteiro_paradas p LEFT JOIN roles r ON r.id=p.role_id WHERE p.roteiro_id=? ORDER BY p.ordem")) {
            s.setInt(1,id);try(var r=s.executeQuery()){while(r.next())stops.add(new Stop(r.getInt("role_id"),r.getString("nome"),r.getString("local_nome"),r.getInt("minutos"),r.getLong("preco_centavos"),r.getBoolean("concluida"),r.getString("horario"),r.getBoolean("cancelado")));}
        }return new Detail(header,stops);
    }
    private Summary summary(ResultSet r) throws SQLException {
        return new Summary(r.getInt("id"),r.getString("nome"),r.getString("cidade"),r.getDate("data_planejada").toString(),r.getString("partida"),r.getInt("pessoas"),r.getInt("duracao_minutos"),r.getLong("orcamento_centavos"),r.getString("modo_orcamento"),r.getLong("total_centavos"),r.getTimestamp("criado_em").toInstant().toString(),r.getInt("total_paradas"),r.getInt("paradas_concluidas"));
    }
}
