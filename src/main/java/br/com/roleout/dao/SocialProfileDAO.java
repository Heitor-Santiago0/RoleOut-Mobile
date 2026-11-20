package br.com.roleout.dao;
import br.com.roleout.model.SocialProfileData.*;
import java.sql.*;
import java.util.*;
public final class SocialProfileDAO {
    private final Connection c;
    public SocialProfileDAO(Connection c){this.c=c;}
    public Own own(int account)throws SQLException {
        try(var s=c.prepareStatement("SELECT id_publico,nome,apresentacao,publico,versao FROM perfis_sociais WHERE conta_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){return r.next()?new Own(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getInt(5)):null;}}
    }
    public boolean lockAccount(int account)throws SQLException{return new ItineraryDAO(c).lockAccount(account);}
    public void save(int account,Own value,boolean insert)throws SQLException {
        try(var s=c.prepareStatement(insert?"INSERT INTO perfis_sociais (nome,apresentacao,publico,versao,id_publico,conta_id) VALUES (?,?,?,?,?,?)":"UPDATE perfis_sociais SET nome=?,apresentacao=?,publico=?,versao=?,id_publico=? WHERE conta_id=?")) {
            s.setString(1,value.nome());s.setString(2,value.apresentacao());s.setBoolean(3,value.publico());s.setInt(4,value.versao());s.setString(5,value.id());s.setInt(6,account);s.executeUpdate();
        }
    }
    public Public view(String id)throws SQLException {
        try(var s=c.prepareStatement("SELECT id_publico,nome,apresentacao FROM perfis_sociais WHERE id_publico=? AND publico=TRUE")){s.setString(1,id);try(var r=s.executeQuery()){return r.next()?new Public(r.getString(1),r.getString(2),r.getString(3)):null;}}
    }
    public List<Public> list(String after)throws SQLException {
        try(var s=c.prepareStatement("SELECT id_publico,nome,apresentacao FROM perfis_sociais WHERE publico=TRUE AND id_publico>? ORDER BY id_publico LIMIT 21")) {
            s.setString(1,after==null?"":after);var items=new ArrayList<Public>();try(var r=s.executeQuery()){while(r.next())items.add(new Public(r.getString(1),r.getString(2),r.getString(3)));}return items;
        }
    }
}
