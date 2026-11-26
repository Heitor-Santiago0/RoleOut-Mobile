package br.com.roleout.api;

import br.com.roleout.service.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;

class PublicationServletTest {
    @Test void missingSessionCannotReadOrUpload()throws Exception {
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        var service=mock(PublicationService.class);new PublicationServlet(mock(AuthService.class),service).service(req,res);
        verify(res).setStatus(401);verifyNoInteractions(service);
    }
    @Test void rejectsOversizedUploadBeforeReadingBodyAndNeverAcceptsOwnerOverride()throws Exception {
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);var auth=mock(AuthService.class);var service=mock(PublicationService.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));when(req.getHeader("Authorization")).thenReturn("Bearer token");when(auth.authenticate("token")).thenReturn(new AuthService.Account(7,"A","a@b.com",false,false));
        when(req.getServletPath()).thenReturn("/api/v1/minhas-publicacoes");when(req.getPathInfo()).thenReturn("/1");when(req.getMethod()).thenReturn("PUT");when(req.getContentType()).thenReturn("application/json");
        when(req.getContentLengthLong()).thenReturn(8500001L);var servlet=new PublicationServlet(auth,service);servlet.service(req,res);verify(res).setStatus(413);verify(req,never()).getReader();verifyNoInteractions(service);
        when(req.getContentLengthLong()).thenReturn(100L);when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{\"contaId\":8}")));servlet.service(req,res);verify(res).setStatus(400);verifyNoInteractions(service);
    }
    @Test void publicEndpointIsReadOnlyAndResponsesAreNotCached()throws Exception {
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);var auth=mock(AuthService.class);var service=mock(PublicationService.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));when(req.getHeader("Authorization")).thenReturn("Bearer token");when(auth.authenticate("token")).thenReturn(new AuthService.Account(7,"A","a@b.com",false,false));
        when(req.getServletPath()).thenReturn("/api/v1/publicacoes");when(req.getMethod()).thenReturn("PUT");new PublicationServlet(auth,service).service(req,res);
        verify(res).setStatus(405);verify(res).setHeader("Cache-Control","no-store");verifyNoInteractions(service);
    }
}
