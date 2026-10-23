package br.com.roleout.api;
import br.com.roleout.service.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import java.io.*;
import static org.mockito.Mockito.*;

class DiscoverySuggestionsServletTest {
    AuthService auth; DiscoverySuggestions suggestions; DiscoverySuggestionsServlet servlet;
    HttpServletRequest request; HttpServletResponse response;
    @BeforeEach void setup() throws Exception {
        auth=mock(AuthService.class);suggestions=mock(DiscoverySuggestions.class);servlet=new DiscoverySuggestionsServlet(auth,suggestions);
        request=mock(HttpServletRequest.class);response=mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getHeader("Authorization")).thenReturn("Bearer test");
        when(auth.authenticate("test")).thenReturn(new AuthService.Account(7,"A","a@example.invalid",false,false));
        when(request.getContentType()).thenReturn("application/json");
    }
    @Test void methodsUseAuthenticatedAccount() throws Exception {
        when(request.getMethod()).thenReturn("GET");servlet.service(request,response);verify(suggestions).get(7);
        when(request.getMethod()).thenReturn("PUT");when(request.getReader()).thenReturn(new BufferedReader(new StringReader("""
            {"ativo":true,"viagens":false,"intervaloHoras":72,"cidadeBase":"Uberaba"}
            """)));
        servlet.service(request,response);verify(suggestions).save(7,new DiscoverySuggestions.Settings(true,false,72,"Uberaba"));
        when(request.getMethod()).thenReturn("POST");when(request.getReader()).thenReturn(new BufferedReader(new StringReader("""
            {"latitude":-19.75,"longitude":-47.93,"cidade":"Uberaba","fuso":"America/Sao_Paulo"}
            """)));
        servlet.service(request,response);verify(suggestions).evaluate(7,new DiscoverySuggestions.Context(-19.75,-47.93,"Uberaba","America/Sao_Paulo"));
    }
    @Test void rejectsAnonymousAndForgedAccountFields() throws Exception {
        when(request.getMethod()).thenReturn("POST");when(request.getHeader("Authorization")).thenReturn(null);
        servlet.service(request,response);verify(response).setStatus(401);verifyNoInteractions(suggestions);
        when(request.getHeader("Authorization")).thenReturn("Bearer test");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"contaId\":999}")));
        servlet.service(request,response);verify(response).setStatus(400);verifyNoInteractions(suggestions);
    }
}
