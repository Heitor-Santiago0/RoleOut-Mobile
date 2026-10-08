package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.OrganizationService;
import br.com.roleout.security.AuthRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationServletTest {
    private AuthService auth;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter output;
    @BeforeEach
    void setup() throws Exception {
        auth = mock(AuthService.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        when(request.getContentType()).thenReturn("application/json");
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/cadastro");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }
    private void body(String text) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(text)));
    }
    @Test
    void signupRejectsAdminMassAssignment() throws Exception {
        body("{\"nome\":\"A\",\"email\":\"a@example.com\",\"senha\":\"uma senha longa\",\"administrador\":true}");
        new AuthServlet(auth).service(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(auth);
    }
    @Test
    void sessionRequiredForOrganizationApi() throws Exception {
        var service = mock(OrganizationService.class);
        new OrganizationServlet(auth, service).service(request, response);
        verify(response).setStatus(401);
        verify(response).setHeader("WWW-Authenticate", "Bearer");
        verifyNoInteractions(service);
    }
    @Test
    void authenticatedOrganizationCreationUsesAccountFromToken() throws Exception {
        var service = mock(OrganizationService.class);
        when(request.getPathInfo()).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer test-token");
        when(auth.authenticate("test-token")).thenReturn(new AuthService.Account(17, "A", "a@example.com", false, false));
        body("{\"nome\":\"Museu\",\"tipo\":\"MUSEU\",\"contatoEmail\":\"a@example.com\"}");
        new OrganizationServlet(auth, service).service(request, response);
        verify(response).setStatus(201);
        verify(service).create(eq(17), any(OrganizationService.OrganizationInput.class));
    }
    @Test
    void organizationRejectsClientChosenStatus() throws Exception {
        var service = mock(OrganizationService.class);
        when(request.getPathInfo()).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer test-token");
        when(auth.authenticate("test-token")).thenReturn(new AuthService.Account(17, "A", "a@example.com", false, false));
        body("{\"nome\":\"Museu\",\"tipo\":\"MUSEU\",\"contatoEmail\":\"a@example.com\",\"status\":\"APROVADA\"}");
        new OrganizationServlet(auth, service).service(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(service);
    }
    @Test
    void rejectsOversizedUnsupportedAndTrailingJsonBodies() throws Exception {
        body("{} {}");
        new AuthServlet(auth).service(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(auth);
        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        when(request.getContentLengthLong()).thenReturn(20000L);
        new AuthServlet(auth).service(request, response);
        verify(response).setStatus(413);
        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        when(request.getContentType()).thenReturn("text/plain");
        new AuthServlet(auth).service(request, response);
        verify(response).setStatus(415);
    }
    @Test
    void limiterCapsAttemptsPerAddress() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        for (int i = 0; i < 10; i++) limiter.check("127.0.0.1");
        assertEquals(429, assertThrows(ApiException.class, () -> limiter.check("127.0.0.1")).status());
        assertDoesNotThrow(() -> limiter.check("127.0.0.2"));
    }
}
