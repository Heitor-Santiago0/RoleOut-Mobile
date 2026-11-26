package br.com.roleout.service;
import br.com.roleout.api.ApiException;
import br.com.roleout.dao.*;
import br.com.roleout.model.ProfileDetailsData.*;
import br.com.roleout.model.PublicationData.Photo;
import java.sql.SQLException;
import java.util.*;
public class ProfileDetailsService {
    private final SqlAccess sql;
    public ProfileDetailsService(){this(new SqlAccess());}
    public ProfileDetailsService(SqlAccess sql){this.sql=sql;}
    public Details get(int viewer,String publicId){
        int account=viewer;Details d;
        try(var c=sql.connection()){
            var dao=new ProfileDetailsDAO(c);
            if(publicId!=null){Integer found=dao.publicAccount(SocialProfileService.validId(publicId));if(found==null)throw new ApiException(404,"PERFIL_INDISPONIVEL","Perfil indisponível.");account=found;}
            d=dao.details(account);
        }catch(SQLException e){throw new RuntimeException(e);}
        List<String> interests=List.of();
        if(d.interessesPublicos()){
            var p=new PreferencesService(sql).get(account).perfil();
            interests=java.util.stream.Stream.of(p.musica(),p.comida(),p.atividades(),p.ambientes()).flatMap(List::stream).distinct().toList();
        }
        return new Details(d.fotoBase64(),d.interessesPublicos(),interests,d.publicacoes(),d.seguidores(),d.seguindo());
    }
    public Details save(int account,Input input){
        if(input.interessesPublicos()==null)throw new IllegalArgumentException("Escolha a privacidade dos interesses.");
        byte[] photo=input.fotoBase64()==null?null:Base64.getDecoder().decode(PublicationPhotos.sanitize(new Photo(input.fotoBase64(),"")).base64());
        try(var c=sql.connection()){
            c.setAutoCommit(false);try{if(!new ItineraryDAO(c).lockAccount(account))throw new IllegalArgumentException("Conta indisponível");new ProfileDetailsDAO(c).save(account,photo,input.interessesPublicos());c.commit();}catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new RuntimeException(e);}return get(account,null);
    }
    public Venue venue(int account,int role,Boolean follow){
        try(var c=sql.connection()){
            c.setAutoCommit(false);try{
                if(!new ItineraryDAO(c).lockAccount(account))throw new IllegalArgumentException("Conta indisponível");
                var dao=new ProfileDetailsDAO(c);var venue=dao.venue(account,role);
                if(venue==null)throw new ApiException(404,"LOCAL_INDISPONIVEL","Local indisponível.");
                if(follow!=null)dao.follow(account,venue.id(),follow);
                var result=dao.venue(account,role);c.commit();return result;
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new RuntimeException(e);}
    }
    public List<Venue> followed(int account){try(var c=sql.connection()){return new ProfileDetailsDAO(c).followed(account);}catch(SQLException e){throw new RuntimeException(e);}}
}
