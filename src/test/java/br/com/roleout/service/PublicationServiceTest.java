package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.model.PublicationData.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationServiceTest {
    private JdbcDataSource source;
    private PublicationService service;
    private SocialProfileService profiles;
    @BeforeEach void setup() throws Exception {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection()) {
            for(String file:List.of("V001__roles.sql","V002__organizacoes_e_locais.sql","V004__roteiros.sql","V008__progresso_roteiro.sql","V011__horario_parada.sql","V014__cancelamento_publicado.sql","V027__perfis_sociais.sql","V028__publicacoes_roteiros.sql","V029__comunidade.sql")) {
                try(var r=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/"+file)),StandardCharsets.UTF_8)){RunScript.execute(c,r);}
            }
        }
        execute("INSERT INTO contas(nome,email,senha_hash) VALUES ('Private name','private@example.com','secret'),('B','b@example.com','secret')");
        execute("INSERT INTO roteiros(conta_id,nome,cidade,data_planejada,partida,duracao_minutos,pessoas,orcamento_centavos,modo_orcamento,total_centavos) VALUES (1,'Private plan','Uberaba','2026-11-26','14:00',180,2,10000,'group',4000)");
        execute("INSERT INTO roteiro_paradas(roteiro_id,ordem,role_id,nome,local_nome,minutos,preco_centavos) VALUES (1,0,99,'Museu','Centro',60,2000)");
        service=new PublicationService(new SqlAccess(source));profiles=new SocialProfileService(new SqlAccess(source));
    }
    private void execute(String query)throws Exception{try(var c=source.getConnection();var s=c.createStatement()){s.executeUpdate(query);}}
    private int count(String table)throws Exception{try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getInt(1);}}
    private Input input(Own old,boolean visible,List<Photo> photos){return new Input(old.id(),"Museus e cafés","Gostei do passeio!",visible,old.versao(),photos);}
    private String publishProfile(){return profiles.save(1,new SocialProfileService.Input("Nome público","Passeios",true,0)).id();}
    private Photo photo()throws Exception {
        var b=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,20,BufferedImage.TYPE_INT_RGB),"png",b);
        b.write("GPS SECRET METADATA".getBytes(StandardCharsets.UTF_8));
        return new Photo(Base64.getEncoder().encodeToString(b.toByteArray()),"  Meu passeio  ");
    }
    @Test void startsPrivateAndRequiresOwnershipAndPublicProfile()throws Exception {
        var initial=service.own(1,1);assertNull(initial.id());assertFalse(initial.publico());assertEquals(0,count("publicacoes_roteiros"));
        assertEquals(404,assertThrows(ApiException.class,()->service.own(2,1)).status());
        assertEquals(404,assertThrows(ApiException.class,()->service.save(2,1,input(initial,false,List.of()))).status());
        assertEquals(409,assertThrows(ApiException.class,()->service.save(1,1,input(initial,true,List.of()))).status());
        assertEquals(0,count("publicacoes_roteiros"));
        var draft=service.save(1,1,input(initial,false,List.of()));assertFalse(draft.publico());
        assertEquals(404,assertThrows(ApiException.class,()->service.view(draft.id())).status());
    }
    @Test void publicationContainsOnlySelectedPublicFieldsAndSanitizedPhotos()throws Exception {
        String author=publishProfile();var saved=service.save(1,1,input(service.own(1,1),true,List.of(photo())));
        var view=service.view(saved.id());var json=new ObjectMapper().valueToTree(view);
        assertEquals(Set.of("id","autor","titulo","relato","cidade","paradas","fotos"),new HashSet<>(new ObjectMapper().convertValue(json,Map.class).keySet()));
        assertFalse(json.toString().contains("private@example.com"));assertFalse(json.toString().contains("Private name"));assertFalse(json.toString().contains("2026-11-26"));
        assertEquals("Nome público",view.autor().nome());assertEquals(1,service.list(author,null).itens().size());
        assertEquals(Set.of("roleId","nome","local","minutos"),new ObjectMapper().convertValue(json.get("paradas").get(0),Map.class).keySet());
        byte[] jpeg=Base64.getDecoder().decode(view.fotos().get(0).base64());
        assertEquals(0xff,jpeg[0]&255);assertEquals(0xd8,jpeg[1]&255);
        assertFalse(new String(jpeg,StandardCharsets.ISO_8859_1).contains("GPS SECRET"));
        assertEquals("Meu passeio",view.fotos().get(0).legenda());assertNotNull(ImageIO.read(new ByteArrayInputStream(jpeg)));
    }
    @Test void editingPublicationNeverChangesPrivatePlanAndSnapshotsRemainStable()throws Exception {
        publishProfile();var saved=service.save(1,1,input(service.own(1,1),true,List.of()));
        execute("UPDATE roteiro_paradas SET nome='Changed privately' WHERE roteiro_id=1");
        var updated=service.save(1,1,new Input(saved.id(),"Outro título","Novo relato",true,saved.versao(),List.of()));
        assertEquals("Museu",updated.paradas().get(0).nome());
        // Use a scoped connection for private persistence assertions.
        try(var c=source.getConnection()){var detail=new br.com.roleout.dao.ItineraryDAO(c).find(1,1);assertEquals("Private plan",detail.roteiro().nome());assertEquals("Changed privately",detail.paradas().get(0).nome());}
    }
    @Test void hideAndProfilePrivacyRevokeDetailAndDirectory()throws Exception {
        String author=publishProfile();var saved=service.save(1,1,input(service.own(1,1),true,List.of(photo())));
        profiles.save(1,new SocialProfileService.Input("Nome público","",false,1));
        assertEquals(404,assertThrows(ApiException.class,()->service.view(saved.id())).status());
        assertEquals(404,assertThrows(ApiException.class,()->service.list(author,null)).status());
        profiles.save(1,new SocialProfileService.Input("Nome público","",true,2));
        var hidden=service.hide(1,1,new Version(saved.id(),saved.versao()));assertFalse(hidden.publico());assertEquals(1,hidden.fotos().size());
        assertEquals(404,assertThrows(ApiException.class,()->service.view(saved.id())).status());assertTrue(service.list(author,null).itens().isEmpty());
        assertEquals(409,assertThrows(ApiException.class,()->service.save(1,1,input(saved,true,List.of()))).status());
    }
    @Test void deletionRemovesPhotosButNotPlanAndStaleEditorCannotOverwriteRecreatedPost()throws Exception {
        publishProfile();var old=service.save(1,1,input(service.own(1,1),true,List.of(photo())));
        assertEquals(404,assertThrows(ApiException.class,()->service.delete(2,1,new Version(old.id(),old.versao()))).status());
        service.delete(1,1,new Version(old.id(),old.versao()));assertEquals(0,count("publicacoes_fotos"));assertEquals(1,count("roteiros"));
        var created=service.save(1,1,input(service.own(1,1),true,List.of()));assertNotEquals(old.id(),created.id());
        assertEquals(409,assertThrows(ApiException.class,()->service.save(1,1,input(old,true,List.of()))).status());
    }
    @Test void deletingSourceRouteCascadesPublicationAndPhotos()throws Exception {
        publishProfile();service.save(1,1,input(service.own(1,1),true,List.of(photo())));
        execute("DELETE FROM roteiros WHERE id=1");assertEquals(0,count("publicacoes_roteiros"));assertEquals(0,count("publicacoes_fotos"));
    }
    @Test void invalidPhotoCannotPartiallyChangeStoryOrExistingPictures()throws Exception {
        publishProfile();var saved=service.save(1,1,input(service.own(1,1),true,List.of(photo())));
        assertThrows(IllegalArgumentException.class,()->service.save(1,1,new Input(saved.id(),"Changed","Wrong",true,saved.versao(),List.of(new Photo("aW52YWxpZA==","")))));
        assertEquals(saved,service.own(1,1));
        var valid=photo();assertThrows(IllegalArgumentException.class,()->service.save(1,1,input(saved,true,Collections.nCopies(4,valid))));
    }
    @Test void rejectsInvalidImageFormatsAndOversizePayloads()throws Exception {
        assertThrows(IllegalArgumentException.class,()->PublicationPhotos.sanitize(new Photo("!", "")));
        assertThrows(IllegalArgumentException.class,()->PublicationPhotos.sanitize(new Photo("A".repeat(2796205), "")));
        assertThrows(IllegalArgumentException.class,()->PublicationPhotos.sanitize(new Photo(Base64.getEncoder().encodeToString("<svg/>".getBytes()), "")));
        var buffer=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1300,100,BufferedImage.TYPE_INT_RGB),"png",buffer);
        var result=PublicationPhotos.sanitize(new Photo(Base64.getEncoder().encodeToString(buffer.toByteArray()),""));
        assertEquals(1280,ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(result.base64()))).getWidth());
    }
    @Test void concurrentEditorsCannotSilentlyOverwriteOneAnother()throws Exception {
        publishProfile();var initial=service.own(1,1);var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task=()->{try{service.save(1,1,input(initial,true,List.of()));return 200;}catch(ApiException e){return e.status();}};
            var results=executor.invokeAll(List.of(task,task));var statuses=new ArrayList<Integer>();for(var f:results)statuses.add(f.get());Collections.sort(statuses);
            assertEquals(List.of(200,409),statuses);assertEquals(1,count("publicacoes_roteiros"));
        }finally{executor.shutdownNow();}
    }
}
