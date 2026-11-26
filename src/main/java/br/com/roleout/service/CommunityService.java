package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.*;
import br.com.roleout.model.CommunityData;
import java.sql.*;
import java.util.*;

public final class CommunityService implements CommunityData {
    private final SqlAccess sql;
    public CommunityService(){this(new SqlAccess());}
    public CommunityService(SqlAccess sql){this.sql=sql;}
    private ApiException missing(){return new ApiException(404,"INDISPONIVEL","Este conteúdo não está disponível.");}
    private interface Action<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(int account,Action<T> action){
        try(var c=sql.connection()){
            c.setAutoCommit(false);
            try{if(!new ItineraryDAO(c).lockAccount(account))throw missing();var result=action.run(c);c.commit();return result;}
            catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new RuntimeException("Community operation failed",e);}
    }
    private void publicProfile(Connection c,int account)throws SQLException {
        var p=new SocialProfileDAO(c).own(account);
        if(p==null||!p.publico())throw new ApiException(409,"PERFIL_PRIVADO","Publique seu perfil para participar da comunidade.");
    }
    private long cursor(Long after){if(after!=null&&after<0)throw new IllegalArgumentException("Página inválida.");return after==null?0:after;}
    private void target(Connection c,String type,String target)throws SQLException {
        if("COMENTARIO".equals(type)){
            SocialProfileService.validId(target);
            if(new CommunityDAO(c).blocked("PUBLICACAO",target)||new PublicationDAO(c).view(target)==null)throw missing();
        }else if("AVALIACAO".equals(type)){
            if(target==null||!target.matches("[1-9][0-9]{0,8}")||new ItineraryDAO(c).availableStop(Integer.parseInt(target))==null)throw missing();
        }else throw new IllegalArgumentException("Tipo inválido.");
    }
    public Feed feed(int account,boolean following,Long after){return transaction(account,c->new CommunityDAO(c).feed(account,following,after==null?Long.MAX_VALUE:cursor(after)));}
    public boolean follows(int account,String profile){SocialProfileService.validId(profile);return transaction(account,c->new CommunityDAO(c).follows(account,profile));}
    public void follow(int account,String profile,boolean value){
        SocialProfileService.validId(profile);
        transaction(account,c->{
            var target=new SocialProfileDAO(c).view(profile);var own=new SocialProfileDAO(c).own(account);
            if(value&&(target==null||(own!=null&&profile.equals(own.id()))))throw missing();
            new CommunityDAO(c).follow(account,profile,value);return null;
        });
    }
    public Entries entries(int account,String type,String target,Long after){long page=cursor(after);return transaction(account,c->{target(c,type,target);return new CommunityDAO(c).entries(account,type,target,page);});}
    public void text(int account,String type,String target,TextInput input){
        if(input==null)throw new IllegalArgumentException("Informe o relato.");
        String text=PublicationService.text(input.texto(),1000,true);
        if("AVALIACAO".equals(type)?input.nota()==null||input.nota()<1||input.nota()>5:input.nota()!=null)throw new IllegalArgumentException("Confira a nota de 1 a 5.");
        transaction(account,c->{
            target(c,type,target);publicProfile(c,account);var dao=new CommunityDAO(c);
            if(type.equals("AVALIACAO")&&!dao.visited(account,Integer.parseInt(target)))throw new ApiException(409,"VISITA_NECESSARIA","Conclua esta parada em um roteiro de hoje ou de uma data passada para avaliar. A visita é autodeclarada.");
            dao.text(account,type,target,text,input.nota());return null;
        });
    }
    public void deleteText(int account,String type,String target){if(!Set.of("COMENTARIO","AVALIACAO").contains(type))throw new IllegalArgumentException("Tipo inválido.");transaction(account,c->{new CommunityDAO(c).deleteText(account,type,target);return null;});}
    public void report(int account,ReportInput input){
        if(input==null||!Set.of("PUBLICACAO","TEXTO").contains(Objects.toString(input.tipo(),"")))throw new IllegalArgumentException("Denúncia inválida.");
        String reason=PublicationService.text(input.motivo(),1000,true);
        transaction(account,c->{var dao=new CommunityDAO(c);
            if(input.tipo().equals("PUBLICACAO"))target(c,"COMENTARIO",input.alvo());
            else{
                if(input.alvo()==null||!input.alvo().matches("[1-9][0-9]{0,17}"))throw missing();
                String parent=dao.textTarget(Long.parseLong(input.alvo()));if(parent==null)throw missing();
                String[] parts=parent.split(":",2);target(c,parts[0],parts[1]);
            }
            if(!dao.reported(account,input.tipo(),input.alvo()))dao.report(account,new ReportInput(input.tipo(),input.alvo(),reason));return null;
        });
    }
    public List<Report> reports(int account,Long after){long page=cursor(after);return transaction(account,c->{admin(c,account);return new CommunityDAO(c).reports(page);});}
    private void admin(Connection c,int account)throws SQLException {if(!new OrganizationDAO(c).admin(account,false))throw new ApiException(403,"SEM_PERMISSAO","Apenas administradores podem analisar denúncias.");}
    public void decide(int account,long id,Decision input){
        if(input==null||input.ocultar()==null)throw new IllegalArgumentException("Informe a decisão.");
        String reason=PublicationService.text(input.justificativa(),1000,true);
        transaction(account,c->{admin(c,account);var dao=new CommunityDAO(c);var item=dao.lockReport(id);if(item==null)throw missing();
            if(!item.estado().equals("PENDENTE"))throw new ApiException(409,"JA_ANALISADO","Esta denúncia já foi analisada.");
            dao.decide(id,account,item,new Decision(input.ocultar(),reason));return null;
        });
    }
    public Credit credit(int account,int route){return transaction(account,c->{if(new ItineraryDAO(c).find(account,route)==null)throw missing();return new CommunityDAO(c).credit(route);});}
    public Credit publicCredit(int account,String publication){return transaction(account,c->{target(c,"COMENTARIO",publication);return new CommunityDAO(c).publicCredit(publication);});}
}
