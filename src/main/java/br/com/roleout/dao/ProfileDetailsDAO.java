package br.com.roleout.dao;
import br.com.roleout.model.ProfileDetailsData.*;
import java.sql.*;
import java.util.*;
public final class ProfileDetailsDAO {
    private final Connection c;
    public ProfileDetailsDAO(Connection c){this.c=c;}
    public Integer publicAccount(String id)throws SQLException {
        try(var s=c.prepareStatement("SELECT conta_id FROM perfis_sociais WHERE id_publico=? AND publico=TRUE")){s.setString(1,id);try(var r=s.executeQuery()){return r.next()?r.getInt(1):null;}}
    }
    public Details details(int account)throws SQLException {
        String photo=null;boolean visible=false;
        try(var s=c.prepareStatement("SELECT foto,interesses_publicos FROM perfil_detalhes WHERE conta_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){if(r.next()){byte[] b=r.getBytes(1);photo=b==null?null:Base64.getEncoder().encodeToString(b);visible=r.getBoolean(2);}}}
        int publications=count("SELECT COUNT(*) FROM publicacoes_roteiros p JOIN perfis_sociais a ON a.conta_id=p.conta_id WHERE p.conta_id=? AND p.publico=TRUE AND a.publico=TRUE AND NOT EXISTS (SELECT 1 FROM comunidade_bloqueios b WHERE b.tipo='PUBLICACAO' AND b.alvo=p.id_publico)",account);
        int followers=count("SELECT COUNT(*) FROM comunidade_seguidos f JOIN perfis_sociais p ON p.id_publico=f.perfil_id WHERE p.conta_id=?",account);
        int following=count("SELECT COUNT(*) FROM comunidade_seguidos f JOIN perfis_sociais p ON p.id_publico=f.perfil_id WHERE f.conta_id=? AND p.publico=TRUE",account);
        return new Details(photo,visible,List.of(),publications,followers,following);
    }
    private int count(String query,int account)throws SQLException {try(var s=c.prepareStatement(query)){s.setInt(1,account);try(var r=s.executeQuery()){r.next();return r.getInt(1);}}}
    public void save(int account,byte[] photo,boolean visible)throws SQLException {
        boolean exists=count("SELECT COUNT(*) FROM perfil_detalhes WHERE conta_id=?",account)>0;
        try(var s=c.prepareStatement(exists?"UPDATE perfil_detalhes SET foto=?,interesses_publicos=? WHERE conta_id=?":"INSERT INTO perfil_detalhes(foto,interesses_publicos,conta_id) VALUES(?,?,?)")){s.setBytes(1,photo);s.setBoolean(2,visible);s.setInt(3,account);s.executeUpdate();}
    }
    public Venue venue(int account,int role)throws SQLException {
        try(var s=c.prepareStatement("SELECT l.id,l.nome,EXISTS(SELECT 1 FROM locais_seguidos f WHERE f.local_id=l.id AND f.conta_id=?) FROM roles r JOIN locais l ON l.id=r.local_id JOIN organizacoes o ON o.id=l.organizacao_id WHERE r.id=? AND l.status='PUBLICADO' AND o.status='APROVADA'")){
            s.setInt(1,account);s.setInt(2,role);try(var r=s.executeQuery()){return r.next()?new Venue(r.getInt(1),r.getString(2),role,r.getBoolean(3)):null;}
        }
    }
    public void follow(int account,int local,boolean value)throws SQLException {
        try(var s=c.prepareStatement(value?"INSERT INTO locais_seguidos(conta_id,local_id) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM locais_seguidos WHERE conta_id=? AND local_id=?)":"DELETE FROM locais_seguidos WHERE conta_id=? AND local_id=?")){
            s.setInt(1,account);s.setInt(2,local);if(value){s.setInt(3,account);s.setInt(4,local);}s.executeUpdate();
        }
    }
    public List<Venue> followed(int account)throws SQLException {
        var items=new ArrayList<Venue>();
        try(var s=c.prepareStatement("SELECT l.id,l.nome,(SELECT MIN(r.id) FROM roles r WHERE r.local_id=l.id) FROM locais_seguidos f JOIN locais l ON l.id=f.local_id JOIN organizacoes o ON o.id=l.organizacao_id WHERE f.conta_id=? AND l.status='PUBLICADO' AND o.status='APROVADA' ORDER BY l.nome LIMIT 100")){s.setInt(1,account);try(var r=s.executeQuery()){while(r.next())items.add(new Venue(r.getInt(1),r.getString(2),r.getObject(3)==null?null:r.getInt(3),true));}}return items;
    }
}
