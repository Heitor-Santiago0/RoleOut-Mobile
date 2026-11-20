package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.SocialProfileDAO;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SocialProfileService implements br.com.roleout.model.SocialProfileData {
    private final SqlAccess sql;
    public SocialProfileService() { this(new SqlAccess()); }
    public SocialProfileService(SqlAccess sql) { this.sql = sql; }

    public Own own(int account) {
        try (var c = sql.connection()) {
            var found=new SocialProfileDAO(c).own(account);
            return found==null?new Own(null,"","",false,0):found;
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
                var dao=new SocialProfileDAO(c);
                if(!dao.lockAccount(account))throw unavailable();
                var old=dao.own(account);int version=old==null?0:old.versao();
                if(version!=input.versao())throw new ApiException(409,"PERFIL_DESATUALIZADO","Seu perfil mudou em outra tela. Recarregue antes de salvar.");
                var saved=new Own(old==null?UUID.randomUUID().toString():old.id(),name,bio,input.publico(),version+1);
                dao.save(account,saved,old==null);
                c.commit();return saved;
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
        try(var c=sql.connection()) {
            var found=new SocialProfileDAO(c).view(id);if(found==null)throw unavailable();return found;
        }catch(SQLException e){throw new RuntimeException("Public profile lookup failed",e);}
    }
    public Page list(String after) {
        if(after!=null)validId(after);
        try(var c=sql.connection()) {
            var items=new SocialProfileDAO(c).list(after);
            boolean more=items.size()>20;if(more)items.remove(20);
            return new Page(List.copyOf(items),more ? items.get(19).id() : null);
        }catch(SQLException e){throw new RuntimeException("Public profile listing failed",e);}
    }
}
