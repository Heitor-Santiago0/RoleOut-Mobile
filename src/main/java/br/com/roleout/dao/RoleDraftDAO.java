package br.com.roleout.dao;
import br.com.roleout.model.RoleDraftData.*;
import br.com.roleout.model.OpeningHours;
import br.com.roleout.model.RoleCharacteristics;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public final class RoleDraftDAO {
    private final Connection c;
    public RoleDraftDAO(Connection c){this.c=c;}
    static Draft map(ResultSet rs) throws SQLException {
        var date = rs.getDate("data");
        int role = rs.getInt("role_id"); Integer roleId = rs.wasNull() ? null : role;
        return new Draft(rs.getInt("id"), rs.getInt("local_id"), rs.getString("nome"),
                rs.getString("descricao"), rs.getString("categoria"), date == null ? null : date.toLocalDate().toString(),
                rs.getBigDecimal("valor"), rs.getInt("versao"), rs.getString("status"), roleId, rs.getString("horario"), rs.getString("horario_fim"), rs.getBoolean("cancelado"), rs.getString("motivo_cancelamento"), rs.getString("data_fim"), OpeningHours.decode(rs.getString("funcionamento")), RoleCharacteristics.decode(rs.getString("caracteristicas")));
    }
    public int save(int venue,Integer id,Input v,RoleCharacteristics characteristics)throws SQLException {
                String query = id == null
                        ? "INSERT INTO rascunhos_roles(nome,descricao,categoria,data,valor,local_id,horario,horario_fim,data_fim,funcionamento,caracteristicas) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
                        : "UPDATE rascunhos_roles SET nome=?,descricao=?,categoria=?,data=?,valor=?,versao=versao+1,status='RASCUNHO',horario=?,horario_fim=?,cancelado=?,motivo_cancelamento=?,data_fim=?,funcionamento=?,caracteristicas=? WHERE local_id=? AND id=? AND versao=?";
                try (var s = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, v.nome()); s.setString(2, v.descricao()); s.setString(3, v.categoria());
                    if (v.data() == null) s.setNull(4, Types.DATE); else s.setDate(4, Date.valueOf(v.data()));
                    s.setBigDecimal(5, v.valor());
                    if (id == null) { s.setInt(6, venue); s.setString(7, v.horario()); s.setString(8, v.horarioFim()); s.setString(9, v.dataFim()); s.setString(10, OpeningHours.encode(v.funcionamento())); s.setString(11, RoleCharacteristics.encode(characteristics)); }
                    else { s.setString(6, v.horario()); s.setString(7, v.horarioFim()); s.setBoolean(8, v.cancelado()); s.setString(9, v.motivoCancelamento()); s.setString(10, v.dataFim()); s.setString(11, OpeningHours.encode(v.funcionamento())); s.setString(12, RoleCharacteristics.encode(characteristics)); s.setInt(13, venue); }
                    if (id != null) { s.setInt(14, id); s.setInt(15, v.versao()); }
                    if (s.executeUpdate() != 1) return 0;
                    if (id == null) {
                        try (var keys = s.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Missing draft ID.");
                            id = keys.getInt(1);
                        }
                    }
                }
        return id;
    }
    public List<Draft> list(int venue,int offset)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM rascunhos_roles WHERE local_id=? ORDER BY id LIMIT 20 OFFSET ?")){s.setInt(1,venue);s.setInt(2,offset);var items=new ArrayList<Draft>();try(var r=s.executeQuery()){while(r.next())items.add(map(r));}return items;}
    }
    public Draft find(int venue,int id,boolean lock)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM rascunhos_roles WHERE id=? AND local_id=?"+(lock?" FOR UPDATE":""))){s.setInt(1,id);s.setInt(2,venue);try(var r=s.executeQuery()){return r.next()?map(r):null;}}
    }
    public void submit(int id)throws SQLException {
        try(var s=c.prepareStatement("UPDATE rascunhos_roles SET status='PENDENTE',versao=versao+1 WHERE id=?")){s.setInt(1,id);s.executeUpdate();}
    }
    public List<Review> reviews(int id)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM revisoes_roles WHERE rascunho_id=? ORDER BY id DESC LIMIT 100")){s.setInt(1,id);var items=new ArrayList<Review>();try(var r=s.executeQuery()){while(r.next())items.add(new Review(r.getInt("id"),r.getInt("versao"),r.getString("decisao"),r.getString("justificativa"),r.getTimestamp("criado_em").toInstant().toString(),r.getString("conteudo")));}return items;}
    }
    public String organizationStatus(int account,int venue,boolean lock)throws SQLException {
        try(var s=c.prepareStatement("SELECT o.status FROM organizacoes o JOIN membros_organizacao m ON m.organizacao_id=o.id WHERE m.conta_id=? AND o.id=(SELECT organizacao_id FROM locais WHERE id=?)"+(lock?" FOR UPDATE":""))){s.setInt(1,account);s.setInt(2,venue);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}}
    }
    public String lockVenue(int id)throws SQLException {
        try(var s=c.prepareStatement("SELECT status FROM locais WHERE id=? FOR UPDATE")){s.setInt(1,id);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}}
    }
    public List<Integer> pending(int offset)throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM rascunhos_roles WHERE status='PENDENTE' ORDER BY id LIMIT 20 OFFSET ?")){s.setInt(1,offset);var ids=new ArrayList<Integer>();try(var r=s.executeQuery()){while(r.next())ids.add(r.getInt(1));}return ids;}
    }
    public record Association(int org,int venue){}
    public Association association(int id)throws SQLException {
        try(var s=c.prepareStatement("SELECT l.organizacao_id,l.id FROM rascunhos_roles r JOIN locais l ON l.id=r.local_id WHERE r.id=?")){s.setInt(1,id);try(var r=s.executeQuery()){return r.next()?new Association(r.getInt(1),r.getInt(2)):null;}}
    }
    public void decide(int id,String status,Integer role,int version,int account,String decision,String reason,String content)throws SQLException {
        try(var s=c.prepareStatement("UPDATE rascunhos_roles SET status=?,role_id=?,versao=versao+1 WHERE id=?")){s.setString(1,status);if(role==null)s.setNull(2,Types.INTEGER);else s.setInt(2,role);s.setInt(3,id);s.executeUpdate();}
        try(var s=c.prepareStatement("INSERT INTO revisoes_roles(rascunho_id,versao,revisor_conta_id,decisao,justificativa,conteudo) VALUES (?,?,?,?,?,?)")){s.setInt(1,id);s.setInt(2,version);s.setInt(3,account);s.setString(4,decision);s.setString(5,reason);s.setString(6,content);s.executeUpdate();}
    }
}
