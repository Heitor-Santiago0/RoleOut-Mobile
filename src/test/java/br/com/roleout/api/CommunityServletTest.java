package br.com.roleout.api;
import br.com.roleout.service.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;

class CommunityServletTest {
    @Test void anonymousCannotReadFeedOrModifyAnything()throws Exception {
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        var service=mock(CommunityService.class);var routes=mock(ItineraryService.class);
        new CommunityServlet(mock(AuthService.class),service,routes).service(req,res);
        verify(res).setStatus(401);verifyNoInteractions(service,routes);
    }
    @Test void unknownAuthorOverrideInCopyIsRejected()throws Exception {
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);var auth=mock(AuthService.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));when(req.getHeader("Authorization")).thenReturn("Bearer token");when(auth.authenticate("token")).thenReturn(new AuthService.Account(2,"A","a@b.com",false,false));
        when(req.getPathInfo()).thenReturn("/copiar/00000000-0000-4000-8000-000000000001");when(req.getMethod()).thenReturn("POST");when(req.getContentType()).thenReturn("application/json");
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{\"planejamento\":null,\"autorId\":1}")));
        var service=mock(CommunityService.class);var routes=mock(ItineraryService.class);new CommunityServlet(auth,service,routes).service(req,res);
        verify(res).setStatus(400);verifyNoInteractions(service,routes);
    }
    @Test void feedUsesAuthenticatedAccountAndDisablesCaching()throws Exception {
        var req=mock(HttpServletRequest.class);var res=mock(HttpServletResponse.class);var auth=mock(AuthService.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));when(req.getHeader("Authorization")).thenReturn("Bearer token");when(auth.authenticate("token")).thenReturn(new AuthService.Account(2,"A","a@b.com",false,false));
        when(req.getPathInfo()).thenReturn("/feed");when(req.getMethod()).thenReturn("GET");when(req.getParameter("seguindo")).thenReturn("true");
        var service=mock(CommunityService.class);new CommunityServlet(auth,service,mock(ItineraryService.class)).service(req,res);
        verify(service).feed(2,true,null);verify(res).setStatus(200);verify(res).setHeader("Cache-Control","no-store");
    }
}
