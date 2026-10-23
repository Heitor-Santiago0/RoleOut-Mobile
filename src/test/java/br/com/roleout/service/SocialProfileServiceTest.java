package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialProfileServiceTest {
    private SocialProfileService service;
    private JdbcDataSource source;
    @BeforeEach void setup() throws Exception {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var c=source.getConnection()) {
            for(String file:List.of("V001__roles.sql","V002__organizacoes_e_locais.sql","V027__perfis_sociais.sql")) {
                try(var r=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/"+file)),StandardCharsets.UTF_8)){RunScript.execute(c,r);}
            }
            try(var s=c.createStatement()){s.executeUpdate("INSERT INTO contas(nome,email,senha_hash) VALUES ('Private Name','private@example.com','secret'),('B','b@example.com','secret')");}
        }
        service=new SocialProfileService(new SqlAccess(source));
    }
    private SocialProfileService.Input input(boolean visible,int version){return new SocialProfileService.Input("  Passeios da Ana  ","Museus e cafés",visible,version);}
    @Test void startsPrivateWithoutCreatingOrCopyingAccountData() throws Exception {
        var empty=service.own(1);assertFalse(empty.publico());assertNull(empty.id());assertEquals("",empty.nome());assertEquals(0,empty.versao());
        assertTrue(service.list(null).itens().isEmpty());
        try(var c=source.getConnection();var s=c.createStatement();var rs=s.executeQuery("SELECT COUNT(*) FROM perfis_sociais")){rs.next();assertEquals(0,rs.getInt(1));}
    }
    @Test void publicOutputContainsOnlyChosenFieldsAndNeverAccountSecrets() throws Exception {
        var saved=service.save(1,input(true,0));var publicProfile=service.view(saved.id());
        assertEquals("Passeios da Ana",publicProfile.nome());assertEquals(3,new ObjectMapper().valueToTree(publicProfile).size());
        String json=new ObjectMapper().writeValueAsString(publicProfile);
        assertFalse(json.contains("private@example.com"));assertFalse(json.contains("Private Name"));assertFalse(json.contains("secret"));
        assertEquals(List.of(publicProfile),service.list(null).itens());assertNull(service.own(2).id());
    }
    @Test void hidingRemovesFromDirectoryAndDirectAccessIncludingOwner() {
        var saved=service.save(1,input(true,0));
        var hidden=service.save(1,input(false,1));assertEquals(saved.id(),hidden.id());
        assertEquals(404,assertThrows(ApiException.class,()->service.view(saved.id())).status());
        assertEquals(404,assertThrows(ApiException.class,()->service.view(UUID.randomUUID().toString())).status());
        assertTrue(service.list(null).itens().isEmpty());assertEquals(hidden,service.own(1));
        assertEquals(saved.id(),service.save(1,input(true,2)).id());
    }
    @Test void staleWritesCannotRepublishHiddenProfile() {
        service.save(1,input(true,0));var hidden=service.save(1,input(false,1));
        assertEquals(409,assertThrows(ApiException.class,()->service.save(1,input(true,1))).status());
        assertEquals(hidden,service.own(1));
        assertEquals(409,assertThrows(ApiException.class,()->service.save(1,input(true,0))).status());
    }
    @Test void invalidInputDoesNotOverwriteProfile() {
        var saved=service.save(1,input(false,0));
        for(var bad:List.of(new SocialProfileService.Input(" ","",true,1),new SocialProfileService.Input("a".repeat(81),"",true,1),new SocialProfileService.Input("A","b".repeat(501),true,1),new SocialProfileService.Input("A","",null,1),new SocialProfileService.Input("A","",true,null))) {
            assertThrows(IllegalArgumentException.class,()->service.save(1,bad));assertEquals(saved,service.own(1));
        }
        assertThrows(IllegalArgumentException.class,()->service.view("1 OR 1=1"));
        assertThrows(IllegalArgumentException.class,()->service.list("not-a-cursor"));
    }
    @Test void directoryPaginationIsBoundedDistinctAndSkipsPrivateProfiles() throws Exception {
        try(var c=source.getConnection();var s=c.prepareStatement("INSERT INTO contas(id,nome,email,senha_hash) VALUES (?, 'Pessoa',?,'x')")) {
            for(int i=3;i<=26;i++){s.setInt(1,i);s.setString(2,i+"@example.com");s.executeUpdate();}
        }
        for(int i=1;i<=26;i++)service.save(i,input(i!=2,0));
        var first=service.list(null);assertEquals(20,first.itens().size());assertNotNull(first.proximo());
        var second=service.list(first.proximo());assertEquals(5,second.itens().size());assertNull(second.proximo());
        var ids=new HashSet<String>();first.itens().forEach(p->assertTrue(ids.add(p.id())));second.itens().forEach(p->assertTrue(ids.add(p.id())));
        assertFalse(ids.contains(service.own(2).id()));
    }
    @Test void concurrentInitialSaveHasOneWinner() throws Exception {
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> write=()->{ready.countDown();go.await();try{service.save(1,input(true,0));return 200;}catch(ApiException e){return e.status();}};
            var a=executor.submit(write);var b=executor.submit(write);ready.await();go.countDown();
            assertEquals(Set.of(200,409),Set.of(a.get(),b.get()));assertEquals(1,service.own(1).versao());
        }finally{executor.shutdownNow();}
    }
}
