package br.com.roleout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrevoRecoveryMailTest {
    private final Map<String,String> env=Map.of("BREVO_API_KEY","test-secret","MAIL_FROM","sender@example.test","MAIL_PROVIDER","brevo");
    @Test void configurationFailsClosedAndKeepsLocalSmtp() {
        assertFalse(new BrevoRecoveryMail(Map.of()).enabled());
        assertFalse(new BrevoRecoveryMail(Map.of("BREVO_API_KEY","key\n","MAIL_FROM","a@b.test")).enabled());
        assertFalse(new BrevoRecoveryMail(Map.of("BREVO_API_KEY","key","MAIL_FROM","invalid")).enabled());
        assertTrue(RecoveryMailProvider.configured(env).enabled());
        assertInstanceOf(SmtpRecoveryMail.class,RecoveryMailProvider.configured(Map.of()));
        assertFalse(RecoveryMailProvider.configured(Map.of("MAIL_PROVIDER","typo")).enabled());
    }
    @SuppressWarnings("unchecked")
    @Test void sendsExactHttpsContract() throws Exception {
        HttpClient client=mock(HttpClient.class); HttpResponse<Void> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201); when(client.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        new BrevoRecoveryMail(env,client).send("person@example.test","A".repeat(43));
        var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(capture.capture(),any());
        var req=capture.getValue();assertEquals("https://api.brevo.com/v3/smtp/email",req.uri().toString());
        assertEquals("POST",req.method());assertEquals("test-secret",req.headers().firstValue("api-key").orElseThrow());
        assertEquals(15,req.timeout().orElseThrow().toSeconds());
        var bytes=new java.io.ByteArrayOutputStream();
        req.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer b){byte[] a=new byte[b.remaining()];b.get(a);bytes.writeBytes(a);}
            public void onError(Throwable t){fail(t);}
            public void onComplete(){}
        });
        var json=new ObjectMapper().readTree(bytes.toString(StandardCharsets.UTF_8));
        assertEquals("sender@example.test",json.at("/sender/email").asText());
        assertEquals("person@example.test",json.at("/to/0/email").asText());
        assertTrue(json.get("textContent").asText().contains("A".repeat(43)));
        assertFalse(json.toString().contains("test-secret"));
    }
    @SuppressWarnings("unchecked")
    @Test void errorsAndRedirectsAreNotSuccessAndNeverExposeProviderBody() throws Exception {
        for(int status:new int[]{200,302,400,401,403,429,500}) {
            HttpClient client=mock(HttpClient.class);HttpResponse<Void> response=mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(status);when(client.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
            var error=assertThrows(IOException.class,()->new BrevoRecoveryMail(env,client).send("p@example.test","A".repeat(43)));
            assertEquals("Recovery delivery failed",error.getMessage());assertNull(error.getCause());
        }
        HttpClient client=mock(HttpClient.class);
        when(client.send(any(),any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("sensitive provider details"));
        assertEquals("Recovery delivery failed",assertThrows(IOException.class,()->new BrevoRecoveryMail(env,client).send("p@example.test","A".repeat(43))).getMessage());
    }
}
