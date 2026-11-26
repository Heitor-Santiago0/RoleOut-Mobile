package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.*;
import br.com.roleout.model.PublicationData;
import java.sql.*;
import java.util.*;

/** Opt-in public snapshot; private planning is never modified by publication. */
public class PublicationService implements PublicationData {
    private final SqlAccess sql;
    public PublicationService(){this(new SqlAccess());}
    public PublicationService(SqlAccess sql){this.sql=sql;}
    static String text(String value,int max,boolean required) {
        if(value==null)throw new IllegalArgumentException("Informe o texto.");
        String clean=value.strip();
        if((required&&clean.isEmpty())||clean.length()>max||clean.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))
            throw new IllegalArgumentException("Confira o tamanho e o conteúdo do texto.");
        return clean;
    }
    private ApiException missing(){return new ApiException(404,"PUBLICACAO_INDISPONIVEL","Esta publicação não está disponível.");}
    private void version(Own old,String id,Integer version) {
        if(version==null||version<0)throw new IllegalArgumentException("Versão inválida.");
        if((old==null?0:old.versao())!=version||!Objects.equals(old==null?null:old.id(),id))
            throw new ApiException(409,"PUBLICACAO_DESATUALIZADA","A publicação mudou. Recarregue antes de salvar.");
    }
    private br.com.roleout.model.ItineraryData.Detail route(Connection c,int account,int route) throws SQLException {
        var detail=new ItineraryDAO(c).find(account,route);
        if(detail==null)throw missing();return detail;
    }
    private List<Stop> snapshot(br.com.roleout.model.ItineraryData.Detail route) {
        return route.paradas().stream().map(p->new Stop(p.roleId(),p.nome(),p.local(),p.minutos())).toList();
    }
    public Own own(int account,int route) {
        try(var c=sql.connection()) {
            var detail=route(c,account,route);
            var old=new PublicationDAO(c).own(account,route);
            return old!=null?old:new Own(null,detail.roteiro().nome().substring(0,Math.min(120,detail.roteiro().nome().length())),"",detail.roteiro().cidade(),snapshot(detail),List.of(),false,0);
        }catch(SQLException e){throw new RuntimeException("Publication lookup failed",e);}
    }
    public Own save(int account,int route,Input input) {
        String title=text(input.titulo(),120,true),story=text(input.relato(),3000,false);
        if(input.publico()==null||input.versao()==null||input.versao()<0||input.fotos()==null||input.fotos().size()>3)
            throw new IllegalArgumentException("Confira a visibilidade, versão e até três fotos.");
        // Check ownership before doing image processing. Recheck under the transaction lock below.
        own(account,route);
        var photos=input.fotos().stream().map(PublicationPhotos::sanitize).toList();
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                var itineraries=new ItineraryDAO(c);
                if(!itineraries.lockAccount(account)||!itineraries.lockOwned(account,route))throw missing();
                var dao=new PublicationDAO(c);var old=dao.own(account,route);
                version(old,input.id(),input.versao());
                if(old!=null&&input.publico()&&new CommunityDAO(c).blocked("PUBLICACAO",old.id()))throw new ApiException(409,"PUBLICACAO_MODERADA","Esta publicação foi ocultada pela moderação.");
                if(old==null&&dao.count(account)>=20)throw new ApiException(409,"LIMITE_PUBLICACOES","Você pode manter até 20 publicações. Exclua uma antes de criar outra.");
                var profile=new SocialProfileDAO(c).own(account);
                if(input.publico()&&(profile==null||!profile.publico()))throw new ApiException(409,"PERFIL_PRIVADO","Publique seu perfil antes de compartilhar o roteiro.");
                var source=route(c,account,route);
                // Existing snapshots stay stable even if private planning changes later.
                var saved=new Own(old==null?UUID.randomUUID().toString():old.id(),title,story,
                    old==null?source.roteiro().cidade():old.cidade(),old==null?snapshot(source):old.paradas(),photos,input.publico(),(old==null?0:old.versao())+1);
                dao.save(account,route,saved,old==null);c.commit();return saved;
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new RuntimeException("Publication save failed",e);}
    }
    public Own hide(int account,int route,Version input) { return changeVisibility(account,route,input,false); }
    public void delete(int account,int route,Version input) { changeVisibility(account,route,input,true); }
    private Own changeVisibility(int account,int route,Version input,boolean delete) {
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                var itineraries=new ItineraryDAO(c);
                if(!itineraries.lockAccount(account)||!itineraries.lockOwned(account,route))throw missing();
                var dao=new PublicationDAO(c);var old=dao.own(account,route);
                if(old==null)throw missing();version(old,input.id(),input.versao());

                if(delete)dao.delete(account,route);else dao.hide(account,route,old.versao()+1);
                var saved=delete?null:dao.own(account,route);c.commit();return saved;
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new RuntimeException("Publication visibility failed",e);}
    }
    public Public view(String id) {
        SocialProfileService.validId(id);
        try(var c=sql.connection()){var result=new PublicationDAO(c).view(id);if(result==null)throw missing();return result;}
        catch(SQLException e){throw new RuntimeException("Public itinerary lookup failed",e);}
    }
    public Page list(String author,String after) {
        SocialProfileService.validId(author);if(after!=null)SocialProfileService.validId(after);
        try(var c=sql.connection()) {
            if(new SocialProfileDAO(c).view(author)==null)throw missing();
            var items=new PublicationDAO(c).list(author,after);boolean more=items.size()>20;
            if(more)items.remove(20);return new Page(List.copyOf(items),more?items.get(19).id():null);
        }catch(SQLException e){throw new RuntimeException("Publication list failed",e);}
    }
}
