package br.com.roleout.api;
import br.com.roleout.service.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.io.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleDraftServletTest {
    AuthService auth; RoleDraftService drafts; RoleDraftServlet servlet;
    HttpServletRequest request; HttpServletResponse response;
    @BeforeEach void setup() throws Exception {
        auth=mock(AuthService.class); drafts=mock(RoleDraftService.class); servlet=new RoleDraftServlet(auth,drafts);
        request=mock(HttpServletRequest.class); response=mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getHeader("Authorization")).thenReturn("Bearer test");
        when(auth.authenticate("test")).thenReturn(new AuthService.Account(7,"Owner","a@example.com",false,false));
        when(request.getPathInfo()).thenReturn("/12"); when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/json");
    }
    @Test void acceptsStructuredHoursAndUsesSessionIdentity() throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"nome\":\"Museu\",\"categoria\":\"Cultura\",\"valor\":0,\"funcionamento\":[{\"dia\":\"5\",\"intervalos\":[{\"inicio\":\"09:00\",\"fim\":\"18:00\"}]}]}")));
        servlet.service(request,response);
        var input=ArgumentCaptor.forClass(RoleDraftService.Input.class);
        verify(drafts).save(eq(7),eq(12),isNull(),input.capture());
        assertEquals("18:00",input.getValue().funcionamento().get(0).intervalos().get(0).fim());
        verify(response).setStatus(201);
    }
    @Test void parsesCharacteristicsWithoutChangingAccountIdentity() throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("""
            {"nome":"Show", "categoria":"Música", "valor":0, "caracteristicas":{"musica":["Rock"],"comida":[],"ambientes":["Animado"]}}
            """)));
        servlet.service(request,response);
        var input=ArgumentCaptor.forClass(RoleDraftService.Input.class);
        verify(drafts).save(eq(7),eq(12),isNull(),input.capture());
        assertEquals(java.util.List.of("Rock"),input.getValue().caracteristicas().musica());
        verify(response).setStatus(201);
    }
    @Test void unauthenticatedAndOversizedRequestsDoNotReachStorage() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        servlet.service(request,response); verify(response).setStatus(401); verifyNoInteractions(drafts);
        clearInvocations(response); when(request.getHeader("Authorization")).thenReturn("Bearer test");
        when(request.getContentLengthLong()).thenReturn(65537L);
        servlet.service(request,response); verify(response).setStatus(413); verifyNoInteractions(drafts);
    }
    @Test void streamingBodyIsBoundedEvenWithoutContentLength() throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(" ".repeat(65537))));
        servlet.service(request,response); verify(response).setStatus(413); verifyNoInteractions(drafts);
    }
}
