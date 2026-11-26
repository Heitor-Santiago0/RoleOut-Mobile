package br.com.roleout.dao;

import br.com.roleout.model.PublicationData.*;
import br.com.roleout.model.SocialProfileData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;

public final class PublicationDAO {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Connection c;
    public PublicationDAO(Connection c) { this.c=c; }
    public Own own(int account,int route) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM publicacoes_roteiros WHERE conta_id=? AND roteiro_id=?")) {
            s.setInt(1,account);s.setInt(2,route);
            try(var r=s.executeQuery()){return r.next()?map(r):null;}
        }
    }
    public int count(int account) throws SQLException {
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM publicacoes_roteiros WHERE conta_id=?")) {
            s.setInt(1,account);try(var r=s.executeQuery()){r.next();return r.getInt(1);}
        }
    }
    private List<Photo> photos(int route) throws SQLException {
        var items=new ArrayList<Photo>();
        try(var s=c.prepareStatement("SELECT imagem,legenda FROM publicacoes_fotos WHERE roteiro_id=? ORDER BY ordem")) {
            s.setInt(1,route);try(var r=s.executeQuery()){while(r.next())items.add(new Photo(Base64.getEncoder().encodeToString(r.getBytes(1)),r.getString(2)));}
        }return List.copyOf(items);
    }
    private List<Stop> stops(String value) throws SQLException {
        try{return JSON.readValue(value,new TypeReference<List<Stop>>(){});}
        catch(java.io.IOException e){throw new SQLException("Invalid publication snapshot",e);}
    }
    private Own map(ResultSet r) throws SQLException {
        return new Own(r.getString("id_publico"),r.getString("titulo"),r.getString("relato"),r.getString("cidade"),
            stops(r.getString("paradas")),photos(r.getInt("roteiro_id")),r.getBoolean("publico"),r.getInt("versao"));
    }
    public void save(int account,int route,Own value,boolean insert) throws SQLException {
        String query=insert?"INSERT INTO publicacoes_roteiros(titulo,relato,cidade,paradas,publico,versao,id_publico,conta_id,roteiro_id) VALUES (?,?,?,?,?,?,?,?,?)":
            "UPDATE publicacoes_roteiros SET titulo=?,relato=?,cidade=?,paradas=?,publico=?,versao=?,id_publico=? WHERE conta_id=? AND roteiro_id=?";
        try(var s=c.prepareStatement(query)) {
            s.setString(1,value.titulo());s.setString(2,value.relato());s.setString(3,value.cidade());
            try{s.setString(4,JSON.writeValueAsString(value.paradas()));}catch(java.io.IOException e){throw new SQLException(e);}
            s.setBoolean(5,value.publico());s.setInt(6,value.versao());s.setString(7,value.id());s.setInt(8,account);s.setInt(9,route);s.executeUpdate();
        }
        try(var s=c.prepareStatement("DELETE FROM publicacoes_fotos WHERE roteiro_id=?")){s.setInt(1,route);s.executeUpdate();}
        try(var s=c.prepareStatement("INSERT INTO publicacoes_fotos(roteiro_id,ordem,legenda,imagem) VALUES (?,?,?,?)")) {
            for(int i=0;i<value.fotos().size();i++){var photo=value.fotos().get(i);s.setInt(1,route);s.setInt(2,i);s.setString(3,photo.legenda());s.setBytes(4,Base64.getDecoder().decode(photo.base64()));s.addBatch();}s.executeBatch();
        }
    }
    public void hide(int account,int route,int version) throws SQLException {
        try(var s=c.prepareStatement("UPDATE publicacoes_roteiros SET publico=FALSE,versao=? WHERE conta_id=? AND roteiro_id=?")) {
            s.setInt(1,version);s.setInt(2,account);s.setInt(3,route);s.executeUpdate();
        }
    }
    public void delete(int account,int route) throws SQLException {
        try(var s=c.prepareStatement("DELETE FROM publicacoes_roteiros WHERE conta_id=? AND roteiro_id=?")){s.setInt(1,account);s.setInt(2,route);s.executeUpdate();}
    }
    public Public view(String id) throws SQLException {
        try(var s=c.prepareStatement("SELECT p.*,a.id_publico AS autor_id,a.nome AS autor_nome,a.apresentacao FROM publicacoes_roteiros p JOIN perfis_sociais a ON a.conta_id=p.conta_id WHERE p.id_publico=? AND p.publico=TRUE AND a.publico=TRUE AND NOT EXISTS(SELECT 1 FROM comunidade_bloqueios b WHERE b.tipo='PUBLICACAO' AND b.alvo=p.id_publico)")) {
            s.setString(1,id);try(var r=s.executeQuery()) {
                if(!r.next())return null;
                var own=map(r);
                return new Public(own.id(),new SocialProfileData.Public(r.getString("autor_id"),r.getString("autor_nome"),r.getString("apresentacao")),own.titulo(),own.relato(),own.cidade(),own.paradas(),own.fotos());
            }
        }
    }
    public List<Card> list(String author,String after) throws SQLException {
        var items=new ArrayList<Card>();
        try(var s=c.prepareStatement("SELECT p.id_publico,p.titulo,p.cidade,p.paradas,(SELECT COUNT(*) FROM publicacoes_fotos f WHERE f.roteiro_id=p.roteiro_id) AS fotos FROM publicacoes_roteiros p JOIN perfis_sociais a ON a.conta_id=p.conta_id WHERE a.id_publico=? AND a.publico=TRUE AND p.publico=TRUE AND NOT EXISTS(SELECT 1 FROM comunidade_bloqueios b WHERE b.tipo='PUBLICACAO' AND b.alvo=p.id_publico) AND p.id_publico>? ORDER BY p.id_publico LIMIT 21")) {
            s.setString(1,author);s.setString(2,after==null?"":after);try(var r=s.executeQuery()){while(r.next())items.add(new Card(r.getString(1),r.getString(2),r.getString(3),stops(r.getString(4)).size(),r.getInt(5)));}
        }return items;
    }
}
