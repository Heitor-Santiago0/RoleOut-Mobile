package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SocialProfileService {
    public record Input(String nome, String apresentacao, Boolean publico, Integer versao) {}
    public record Own(String id, String nome, String apresentacao, boolean publico, int versao) {}
    public record Public(String id, String nome, String apresentacao) {}
    public record Page(List<Public> itens, String proximo) {}
    private final SqlAccess sql;
    public SocialProfileService() { this(new SqlAccess()); }
    public SocialProfileService(SqlAccess sql) { this.sql = sql; }

    public Own own(int account) {
        try (var c = sql.connection(); var s = c.prepareStatement("SELECT id_publico,nome,apresentacao,publico,versao FROM perfis_sociais WHERE conta_id=?")) {
            s.setInt(1,account);
            try (var rs=s.executeQuery()) {
                return rs.next() ? new Own(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getInt(5))
                    : new Own(null,"","",false,0);
            }
        } catch (SQLException e) { throw new RuntimeException("Social profile lookup failed",e); }
    }
    private String text(String value, int max, boolean required) {
        if(value==null) throw new IllegalArgumentException("Missing text");
        String clean=value.strip();
        if((required && clean.isEmpty()) || clean.length()>max || clean.codePoints().anyMatch(c -> Character.isISOControl(c) && c!='\n'))
            throw new IllegalArgumentException("Invalid profile text");
        return clean;
    }
    public Own save(int account, Input input) {
        String name=text(input.nome(),80,true), bio=text(input.apresentacao(),500,false);
        if(input.publico()==null || input.versao()==null || input.versao()<0) throw new IllegalArgumentException("Missing privacy/version");
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                try(var lock=c.prepareStatement("SELECT id FROM contas WHERE id=? FOR UPDATE")) {
                    lock.setInt(1,account);
                    try(var rs=lock.executeQuery()) { if(!rs.next()) throw unavailable(); }
                }
                String id=null; int version=0;
                try(var s=c.prepareStatement("SELECT id_publico,versao FROM perfis_sociais WHERE conta_id=?")) {
                    s.setInt(1,account);
                    try(var rs=s.executeQuery()) { if(rs.next()){id=rs.getString(1);version=rs.getInt(2);} }
                }
                if(version!=input.versao()) throw new ApiException(409,"PERFIL_DESATUALIZADO","Seu perfil mudou em outra tela. Recarregue antes de salvar.");
                if(id==null) {
                    id=UUID.randomUUID().toString();
                    try(var s=c.prepareStatement("INSERT INTO perfis_sociais (nome,apresentacao,publico,versao,id_publico,conta_id) VALUES (?,?,?,?,?,?)")) {
                        s.setString(1,name);s.setString(2,bio);s.setBoolean(3,input.publico());s.setInt(4,1);s.setString(5,id);s.setInt(6,account);s.executeUpdate();
                    }
                } else {
                    try(var s=c.prepareStatement("UPDATE perfis_sociais SET nome=?,apresentacao=?,publico=?,versao=? WHERE conta_id=?")) {
                        s.setString(1,name);s.setString(2,bio);s.setBoolean(3,input.publico());s.setInt(4,version+1);s.setInt(5,account);s.executeUpdate();
                    }
                }
                c.commit();return new Own(id,name,bio,input.publico(),version+1);
            } catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new RuntimeException("Social profile save failed",e);}
    }
    public static String validId(String id) {
        if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid public ID");
        return id;
    }
    private ApiException unavailable(){return new ApiException(404,"PERFIL_INDISPONIVEL","Este perfil não está disponível.");}
    public Public view(String id) {
        validId(id);
        try(var c=sql.connection();var s=c.prepareStatement("SELECT id_publico,nome,apresentacao FROM perfis_sociais WHERE id_publico=? AND publico=TRUE")) {
            s.setString(1,id);
            try(var rs=s.executeQuery()) {if(!rs.next())throw unavailable();return new Public(rs.getString(1),rs.getString(2),rs.getString(3));}
        }catch(SQLException e){throw new RuntimeException("Public profile lookup failed",e);}
    }
    public Page list(String after) {
        if(after!=null)validId(after);
        try(var c=sql.connection();var s=c.prepareStatement("SELECT id_publico,nome,apresentacao FROM perfis_sociais WHERE publico=TRUE AND id_publico>? ORDER BY id_publico LIMIT 21")) {
            s.setString(1,after==null ? "" : after);
            var items=new ArrayList<Public>();
            try(var rs=s.executeQuery()){while(rs.next())items.add(new Public(rs.getString(1),rs.getString(2),rs.getString(3)));}
            boolean more=items.size()>20;if(more)items.remove(20);
            return new Page(List.copyOf(items),more ? items.get(19).id() : null);
        }catch(SQLException e){throw new RuntimeException("Public profile listing failed",e);}
    }
}
