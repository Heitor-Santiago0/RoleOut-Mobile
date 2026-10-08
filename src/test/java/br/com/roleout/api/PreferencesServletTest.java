package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.PreferencesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;

class PreferencesServletTest {
    @Test void authenticationIsRequiredBeforeReadingPreferences() throws Exception {
        var service = mock(PreferencesService.class);
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        new PreferencesServlet(mock(AuthService.class), service).service(req, res);
        verify(res).setStatus(401); verifyNoInteractions(service);
    }
    @Test void rejectsClientSuppliedOwner() throws Exception {
        var service = mock(PreferencesService.class); var auth = mock(AuthService.class);
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        when(auth.authenticate("token")).thenReturn(new AuthService.Account(7, "A", "a@example.com", false, false));
        when(req.getMethod()).thenReturn("POST"); when(req.getContentType()).thenReturn("application/json");
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{\"contaId\":8}")));
        new PreferencesServlet(auth, service).service(req, res);
        verify(res).setStatus(400); verifyNoInteractions(service);
    }
}
