package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.SocialProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;

class SocialProfileServletTest {
    @Test void unauthenticatedDirectoryNeverReadsData() throws Exception {
        var profiles=mock(SocialProfileService.class);var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        new SocialProfileServlet(mock(AuthService.class),profiles).service(req,res);
        verify(res).setStatus(401);verifyNoInteractions(profiles);
    }
    @Test void ownerIsAlwaysAuthenticatedAndClientCannotSupplyAnotherAccount() throws Exception {
        var auth=mock(AuthService.class);var profiles=mock(SocialProfileService.class);
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(req.getHeader("Authorization")).thenReturn("Bearer token");when(auth.authenticate("token")).thenReturn(new AuthService.Account(7,"A","a@example.com",false,false));
        when(req.getServletPath()).thenReturn("/api/v1/perfil-social");when(req.getMethod()).thenReturn("GET");
        var servlet=new SocialProfileServlet(auth,profiles);servlet.service(req,res);verify(profiles).own(7);clearInvocations(profiles);
        when(req.getMethod()).thenReturn("PUT");when(req.getContentType()).thenReturn("application/json");
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{\"nome\":\"Outro\",\"apresentacao\":\"\",\"publico\":true,\"versao\":0,\"contaId\":8}")));
        servlet.service(req,res);verify(res).setStatus(400);verifyNoInteractions(profiles);
    }
    @Test void publicRoutesDoNotAcceptWrites() throws Exception {
        var auth=mock(AuthService.class);var profiles=mock(SocialProfileService.class);
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(req.getHeader("Authorization")).thenReturn("Bearer token");when(auth.authenticate("token")).thenReturn(new AuthService.Account(7,"A","a@example.com",false,false));
        when(req.getServletPath()).thenReturn("/api/v1/perfis");when(req.getMethod()).thenReturn("PUT");
        new SocialProfileServlet(auth,profiles).service(req,res);verify(res).setStatus(405);verifyNoInteractions(profiles);
    }
}
