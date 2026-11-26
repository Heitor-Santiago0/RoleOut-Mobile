package br.com.roleout.service;
import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.model.CommunityData.*;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityServiceTest {
    JdbcDataSource source; CommunityService community; PublicationService publications; ItineraryService routes; SocialProfileService profiles;
    String author,post; int route;
    @BeforeEach void setup()throws Exception {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection()){
            for(String file:List.of("V001__roles.sql","V002__organizacoes_e_locais.sql","V003__sessoes.sql","V004__roteiros.sql","V008__progresso_roteiro.sql","V009__horario_evento.sql","V011__horario_parada.sql","V012__termino_evento.sql","V014__cancelamento_publicado.sql","V016__data_termino_evento.sql","V018__funcionamento_evento.sql","V020__horarios_salvos.sql","V027__perfis_sociais.sql","V028__publicacoes_roteiros.sql","V029__comunidade.sql","V005__preferencias.sql","V030__profile_details_and_venue_follows.sql"))
                try(var r=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/"+file)),StandardCharsets.UTF_8)){RunScript.execute(c,r);}
        }
        execute("INSERT INTO contas(nome,email,senha_hash,administrador) VALUES ('Private A','a@example.com','x',false),('Private B','b@example.com','x',false),('Admin','c@example.com','x',true)");
        execute("INSERT INTO roles(nome,cidade,local,valor,categoria) VALUES ('Museu','Uberaba','Centro',10,'Cultura'),('Parque','Uberaba','Jardim',0,'Natureza')");
        var sql=new SqlAccess(source);community=new CommunityService(sql);publications=new PublicationService(sql);routes=new ItineraryService(sql);profiles=new SocialProfileService(sql);
        author=profiles.save(1,new SocialProfileService.Input("Autor público","",true,0)).id();
        route=routes.create(1,plan("2026-11-26",5000)).roteiro().id();
        post=publications.save(1,route,new PublicationService.Input(null,"Passeio","Gostei",true,0,List.of())).id();
    }
    void execute(String sql)throws Exception{try(var c=source.getConnection();var s=c.createStatement()){s.executeUpdate(sql);}}
    int count(String table)throws Exception{try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getInt(1);}}
    ItineraryService.Input plan(String date,long budget){return new ItineraryService.Input("Minha cópia","Uberaba",date,"10:00",180,1,budget,"group",List.of(new ItineraryService.StopInput(1,60)));}
    @Test void profileDetailsKeepInterestsPrivateAndCountOnlyVisiblePublications()throws Exception {
        var details=new ProfileDetailsService(new SqlAccess(source));
        new PreferencesService(new SqlAccess(source)).save(1,new PreferencesService.Input(false,List.of("Jazz"),List.of("Mineira"),List.of("História e patrimônio"),List.of("Com amigos")));
        assertTrue(details.get(2,author).interesses().isEmpty());
        assertEquals(1,details.get(2,author).publicacoes());
        community.follow(2,author,true);assertEquals(1,details.get(2,author).seguidores());
        details.save(1,new br.com.roleout.model.ProfileDetailsData.Input(null,true));
        assertTrue(details.get(2,author).interesses().contains("Jazz"));
        assertTrue(details.get(2,null).interesses().isEmpty());
        details.save(1,new br.com.roleout.model.ProfileDetailsData.Input(null,false));
        assertTrue(details.get(2,author).interesses().isEmpty());
        execute("INSERT INTO comunidade_bloqueios(tipo,alvo) VALUES ('PUBLICACAO','"+post+"')");
        assertEquals(0,details.get(2,author).publicacoes());
        profiles.save(1,new SocialProfileService.Input("Autor público","",false,1));
        assertEquals(404,assertThrows(ApiException.class,()->details.get(2,author)).status());
        assertThrows(IllegalArgumentException.class,()->details.save(1,new br.com.roleout.model.ProfileDetailsData.Input("not-an-image",false)));
    }
    @Test void venueFollowingIsIdempotentAccountScopedAndRespectsApproval()throws Exception {
        execute("INSERT INTO organizacoes(nome,tipo,contato_email,status) VALUES ('Catálogo','OUTRO','catalog@example.com','APROVADA')");
        execute("INSERT INTO locais(organizacao_id,nome,categoria,endereco,cidade,uf,status) VALUES (1,'Parque','Natureza','Centro','Uberaba','MG','PUBLICADO')");
        execute("UPDATE roles SET local_id=1 WHERE id IN (1,2)");
        var details=new ProfileDetailsService(new SqlAccess(source));
        assertTrue(details.venue(1,1,true).seguindo());
        assertTrue(details.venue(1,2,true).seguindo());assertEquals(1,details.followed(1).size());
        assertFalse(details.venue(2,1,null).seguindo());assertTrue(details.followed(2).isEmpty());
        assertFalse(details.venue(1,2,false).seguindo());assertTrue(details.followed(1).isEmpty());
        execute("UPDATE locais SET status='SUSPENSO' WHERE id=1");
        assertEquals(404,assertThrows(ApiException.class,()->details.venue(1,1,true)).status());
    }
    @Test void feedAndFollowingRespectBothPrivacyFlags(){
        assertEquals(1,community.feed(2,false,null).itens().size());assertTrue(community.feed(2,true,null).itens().isEmpty());
        community.follow(2,author,true);community.follow(2,author,true);assertTrue(community.follows(2,author));assertEquals(1,community.feed(2,true,null).itens().size());
        profiles.save(1,new SocialProfileService.Input("Autor público","",false,1));assertTrue(community.feed(2,false,null).itens().isEmpty());
        community.follow(2,author,false);assertFalse(community.follows(2,author));
    }
    @Test void commentsAreOwnedEditableAndPrivateProfilesDisappear(){
        assertEquals(409,assertThrows(ApiException.class,()->community.text(2,"COMENTARIO",post,new TextInput("Legal",null))).status());
        profiles.save(2,new SocialProfileService.Input("Visitante","",true,0));
        community.text(2,"COMENTARIO",post,new TextInput("Legal",null));community.text(2,"COMENTARIO",post,new TextInput("Adorei",null));
        var a=community.entries(1,"COMENTARIO",post,null);assertEquals(1,a.total());assertFalse(a.itens().get(0).meu());assertEquals("Adorei",a.itens().get(0).texto());
        community.deleteText(1,"COMENTARIO",post);assertEquals(1,community.entries(2,"COMENTARIO",post,null).total());
        profiles.save(2,new SocialProfileService.Input("Visitante","",false,1));assertEquals(0,community.entries(1,"COMENTARIO",post,null).total());
        community.deleteText(2,"COMENTARIO",post);assertEquals(0,countUnchecked("comunidade_textos"));
    }
    int countUnchecked(String table){try{return count(table);}catch(Exception e){throw new RuntimeException(e);}}
    @Test void ratingsRequireDeclaredPastVisitAndComputeCurrentAverage()throws Exception {
        profiles.save(2,new SocialProfileService.Input("Visitante","",true,0));
        assertEquals(409,assertThrows(ApiException.class,()->community.text(2,"AVALIACAO","1",new TextInput("Bom",5))).status());
        var copy=routes.copy(2,post,plan("2001-01-01",5000));routes.progress(2,copy.roteiro().id(),1,new ItineraryService.Progress(true));
        community.text(2,"AVALIACAO","1",new TextInput("Bom",5));assertEquals(5.0,community.entries(1,"AVALIACAO","1",null).media());
        community.text(2,"AVALIACAO","1",new TextInput("Atualizado",3));assertEquals(3.0,community.entries(1,"AVALIACAO","1",null).media());
        assertThrows(IllegalArgumentException.class,()->community.text(2,"AVALIACAO","1",new TextInput("Bom",6)));
        execute("UPDATE roteiros SET data_planejada='2099-01-01' WHERE conta_id=2");
        assertThrows(ApiException.class,()->community.text(2,"AVALIACAO","1",new TextInput("Futuro",5)));
    }
    @Test void copyRevalidatesAndKeepsAuthorAndOriginalUnchanged()throws Exception {
        var before=routes.find(1,route);var copy=routes.copy(2,post,plan("2027-04-01",5000));
        assertNotEquals(route,copy.roteiro().id());assertEquals("2027-04-01",copy.roteiro().data());assertEquals(1000,copy.roteiro().totalCentavos());assertEquals(before,routes.find(1,route));
        var credit=community.credit(2,copy.roteiro().id());assertEquals(author,credit.autorId());assertEquals(post,credit.publicacaoId());
        assertEquals(404,assertThrows(ApiException.class,()->community.credit(1,copy.roteiro().id())).status());
        execute("UPDATE roles SET valor=100 WHERE id=1");assertEquals(409,assertThrows(ApiException.class,()->routes.copy(2,post,plan("2027-04-01",5000))).status());
        assertEquals(2,count("roteiros"));assertEquals(1,count("roteiro_creditos"));
        execute("UPDATE roles SET cancelado=TRUE WHERE id=1");assertThrows(ApiException.class,()->routes.copy(2,post,plan("2027-04-01",50000)));
    }
    @Test void copyRejectsIncompatibleDatesHoursAndMissingSource()throws Exception {
        execute("UPDATE roles SET data='2027-01-01',horario='09:00',horario_fim='09:30' WHERE id=1");
        assertThrows(ApiException.class,()->routes.copy(2,post,plan("2027-01-02",5000)));
        assertThrows(ApiException.class,()->routes.copy(2,post,plan("2027-01-01",5000)));
        var old=publications.own(1,route);publications.hide(1,route,new PublicationService.Version(post,old.versao()));
        assertEquals(404,assertThrows(ApiException.class,()->routes.copy(2,post,plan("2027-01-01",5000))).status());assertEquals(1,count("roteiros"));
    }
    @Test void reportsAreIdempotentAndModerationProtectsAllPublicReads()throws Exception {
        var report=new ReportInput("PUBLICACAO",post,"Conteúdo inadequado");community.report(2,report);community.report(2,report);assertEquals(1,count("comunidade_denuncias"));
        assertEquals(403,assertThrows(ApiException.class,()->community.reports(2,null)).status());
        var item=community.reports(3,null).get(0);assertTrue(item.conteudo().contains("Passeio"));
        community.decide(3,item.id(),new Decision(true,"Confirmado"));
        assertTrue(community.feed(2,false,null).itens().isEmpty());assertTrue(publications.list(author,null).itens().isEmpty());assertThrows(ApiException.class,()->publications.view(post));
        assertThrows(ApiException.class,()->routes.copy(2,post,plan("2027-01-01",5000)));
        var old=publications.own(1,route);assertThrows(ApiException.class,()->publications.save(1,route,new PublicationService.Input(post,"Novo título","",true,old.versao(),List.of())));
        assertEquals(409,assertThrows(ApiException.class,()->community.decide(3,item.id(),new Decision(false,"Não"))).status());
    }
    @Test void moderatedCommentStaysHiddenOnEdit(){
        community.text(1,"COMENTARIO",post,new TextInput("Ruim",null));var entry=community.entries(2,"COMENTARIO",post,null).itens().get(0);
        community.report(2,new ReportInput("TEXTO",String.valueOf(entry.id()),"Ofensa"));var report=community.reports(3,null).get(0);
        community.decide(3,report.id(),new Decision(true,"Ofensa confirmada"));community.text(1,"COMENTARIO",post,new TextInput("Outro",null));
        assertEquals(0,community.entries(2,"COMENTARIO",post,null).total());
    }
    @Test void creditsSurviveOriginalDeletionAndInheritedCopies()throws Exception {
        var copy=routes.copy(2,post,plan("2027-01-01",5000));profiles.save(2,new SocialProfileService.Input("Segundo autor","",true,0));
        String second=publications.save(2,copy.roteiro().id(),new PublicationService.Input(null,"Minha versão","",true,0,List.of())).id();
        var third=routes.copy(3,second,plan("2027-01-01",5000));assertEquals(author,community.credit(3,third.roteiro().id()).autorId());
        routes.delete(1,route);assertEquals(author,community.credit(2,copy.roteiro().id()).autorId());assertNotNull(routes.find(2,copy.roteiro().id()));
    }
}
