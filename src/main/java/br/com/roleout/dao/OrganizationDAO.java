package br.com.roleout.dao;
import br.com.roleout.model.OrganizationData.*;
import java.sql.*;
import java.util.*;
public final class OrganizationDAO {
    private final Connection c;
    public OrganizationDAO(Connection c){this.c=c;}
    static Organization organization(ResultSet rs) throws SQLException {
        return new Organization(rs.getInt("id"), rs.getString("nome"), rs.getString("tipo"),
                rs.getString("descricao"), rs.getString("contato_email"), rs.getString("contato_telefone"),
                rs.getString("status"), rs.getString("papel"));
    }
    static Venue venue(ResultSet rs) throws SQLException {
        return new Venue(rs.getInt("id"), rs.getInt("organizacao_id"), rs.getString("nome"),
                rs.getString("categoria"), rs.getString("endereco"), rs.getString("cidade"), rs.getString("uf"),
                rs.getString("cep"), rs.getString("descricao"), rs.getString("acessibilidade"), rs.getString("status"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"));
    }
    private void bindOrganization(java.sql.PreparedStatement s, OrganizationInput v) throws SQLException {
        s.setString(1, v.nome()); s.setString(2, v.tipo()); s.setString(3, v.descricao());
        s.setString(4, v.contatoEmail()); s.setString(5, v.contatoTelefone());
    }
    public int insertOrganization(OrganizationInput value)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO organizacoes (nome,tipo,descricao,contato_email,contato_telefone) VALUES (?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            bindOrganization(s,value);s.executeUpdate();try(var r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Missing organization ID");return r.getInt(1);}
        }
    }
    public void addOwner(int org,int account)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO membros_organizacao (organizacao_id,conta_id,papel) VALUES (?,?,'RESPONSAVEL')")){s.setInt(1,org);s.setInt(2,account);s.executeUpdate();}
    }
    public List<Organization> list(int account)throws SQLException {
        try(var s=c.prepareStatement("SELECT o.*,m.papel FROM organizacoes o JOIN membros_organizacao m ON m.organizacao_id=o.id WHERE m.conta_id=? ORDER BY o.id LIMIT 100")) {
            s.setInt(1,account);var items=new ArrayList<Organization>();try(var r=s.executeQuery()){while(r.next())items.add(organization(r));}return items;
        }
    }
    public void updateOrganization(int id,OrganizationInput value)throws SQLException {
        try(var s=c.prepareStatement("UPDATE organizacoes SET nome=?,tipo=?,descricao=?,contato_email=?,contato_telefone=?,status='PENDENTE' WHERE id=?")){bindOrganization(s,value);s.setInt(6,id);s.executeUpdate();}
    }
    public int saveVenue(int org,Integer id,VenueInput v)throws SQLException {
        String query=id==null?"INSERT INTO locais (nome,categoria,endereco,cidade,uf,cep,descricao,acessibilidade,latitude,longitude,organizacao_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)":"UPDATE locais SET nome=?,categoria=?,endereco=?,cidade=?,uf=?,cep=?,descricao=?,acessibilidade=?,latitude=?,longitude=?,status='RASCUNHO' WHERE organizacao_id=? AND id=?";
        try(var s=c.prepareStatement(query,Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1,v.nome());s.setString(2,v.categoria());s.setString(3,v.endereco());s.setString(4,v.cidade());s.setString(5,v.uf());s.setString(6,v.cep());s.setString(7,v.descricao());s.setString(8,v.acessibilidade());s.setBigDecimal(9,v.latitude());s.setBigDecimal(10,v.longitude());s.setInt(11,org);
            if(id!=null)s.setInt(12,id);if(s.executeUpdate()!=1)return 0;
            if(id!=null)return id;try(var r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Missing venue ID");return r.getInt(1);}
        }
    }
    public List<Venue> venues(int org)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM locais WHERE organizacao_id=? ORDER BY id LIMIT 100")){s.setInt(1,org);var items=new ArrayList<Venue>();try(var r=s.executeQuery()){while(r.next())items.add(venue(r));}return items;}
    }
    public void submitVenue(int org,int id)throws SQLException {
        try(var s=c.prepareStatement("UPDATE locais SET status='PENDENTE' WHERE id=? AND organizacao_id=?")){s.setInt(1,id);s.setInt(2,org);s.executeUpdate();}
    }
    public List<Review> reviews(int org)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM revisoes_cadastro WHERE organizacao_id=? ORDER BY id DESC LIMIT 100")) {
            s.setInt(1,org);var items=new ArrayList<Review>();try(var r=s.executeQuery()){while(r.next()){int id=r.getInt("local_id");Integer local=r.wasNull()?null:id;items.add(new Review(r.getInt("id"),local,r.getString("decisao"),r.getString("justificativa"),r.getTimestamp("criado_em").toInstant().toString()));}}return items;
        }
    }
    public Organization member(int account,int org,boolean lock)throws SQLException {
        try(var s=c.prepareStatement("SELECT o.*,m.papel FROM organizacoes o JOIN membros_organizacao m ON m.organizacao_id=o.id WHERE o.id=? AND m.conta_id=?"+(lock?" FOR UPDATE":""))){s.setInt(1,org);s.setInt(2,account);try(var r=s.executeQuery()){return r.next()?organization(r):null;}}
    }
    public Organization organization(int org,boolean lock)throws SQLException {
        try(var s=c.prepareStatement("SELECT o.*, '' AS papel FROM organizacoes o WHERE id=?"+(lock?" FOR UPDATE":""))){s.setInt(1,org);try(var r=s.executeQuery()){return r.next()?organization(r):null;}}
    }
    public Venue venue(int org,int id,boolean lock)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM locais WHERE id=? AND organizacao_id=?"+(lock?" FOR UPDATE":""))){s.setInt(1,id);s.setInt(2,org);try(var r=s.executeQuery()){return r.next()?venue(r):null;}}
    }
    public boolean admin(int account,boolean lock)throws SQLException {
        try(var s=c.prepareStatement("SELECT administrador FROM contas WHERE id=?"+(lock?" FOR UPDATE":""))){s.setInt(1,account);try(var r=s.executeQuery()){return r.next()&&r.getBoolean(1);}}
    }
    public boolean hasMember(int account,int org)throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM membros_organizacao WHERE conta_id=? AND organizacao_id=?")){s.setInt(1,account);s.setInt(2,org);try(var r=s.executeQuery()){return r.next();}}
    }
    public List<int[]> pending(int offset)throws SQLException {
        try(var s=c.prepareStatement("SELECT id AS org_id, 0 AS local_id FROM organizacoes WHERE status='PENDENTE' UNION ALL SELECT organizacao_id AS org_id, id AS local_id FROM locais WHERE status='PENDENTE' ORDER BY org_id,local_id LIMIT 20 OFFSET ?")) {
            s.setInt(1,offset);var keys=new ArrayList<int[]>();try(var r=s.executeQuery()){while(r.next())keys.add(new int[]{r.getInt(1),r.getInt(2)});}return keys;
        }
    }
    public void decide(int org,Integer venue,int account,String next,String decision,String reason)throws SQLException {
        try(var s=c.prepareStatement(venue==null?"UPDATE organizacoes SET status=? WHERE id=?":"UPDATE locais SET status=? WHERE id=?")){s.setString(1,next);s.setInt(2,venue==null?org:venue);s.executeUpdate();}
        try(var s=c.prepareStatement("INSERT INTO revisoes_cadastro (organizacao_id,local_id,revisor_conta_id,decisao,justificativa) VALUES (?,?,?,?,?)")){s.setInt(1,org);if(venue==null)s.setNull(2,Types.INTEGER);else s.setInt(2,venue);s.setInt(3,account);s.setString(4,decision);s.setString(5,reason);s.executeUpdate();}
    }
}
