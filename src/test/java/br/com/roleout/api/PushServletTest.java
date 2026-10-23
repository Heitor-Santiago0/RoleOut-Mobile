package br.com.roleout.api;
import br.com.roleout.service.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;

class PushServletTest {
    @Test void requiresAuthenticationAndNeverAcceptsClientAccountIdentity() throws Exception {
        var auth=mock(AuthService.class);var push=mock(PushService.class);var servlet=new PushServlet(auth,push);
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(req.getMethod()).thenReturn("GET");servlet.service(req,res);verify(res).setStatus(401);verifyNoInteractions(push);
        when(req.getHeader("Authorization")).thenReturn("Bearer token");when(auth.authenticate("token")).thenReturn(new AuthService.Account(7,"A","a@test.com",false,false));
        servlet.service(req,res);verify(push).status("token");reset(push);clearInvocations(res);
        when(req.getMethod()).thenReturn("PUT");when(req.getContentType()).thenReturn("application/json");
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{\"expoToken\":\"ExpoPushToken[abcdefghijk]\",\"contaId\":8}")));
        servlet.service(req,res);verify(res).setStatus(400);verifyNoInteractions(push);clearInvocations(res);
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{\"expoToken\":\"ExpoPushToken[abcdefghijk]\"}")));
        servlet.service(req,res);verify(push).register(7,"token",new PushService.Registration("ExpoPushToken[abcdefghijk]"));
        when(req.getMethod()).thenReturn("DELETE");servlet.service(req,res);verify(push).remove("token");
        reset(push);clearInvocations(res);when(req.getMethod()).thenReturn("POST");servlet.service(req,res);verify(res).setStatus(405);verifyNoInteractions(push);
    }
}
