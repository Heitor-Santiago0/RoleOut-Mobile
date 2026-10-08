package br.com.roleout.api;
import br.com.roleout.service.AuthService;
import br.com.roleout.service.ItineraryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ItineraryServletTest {
    private AuthService auth; private ItineraryService plans; private ItineraryServlet servlet;
    private HttpServletRequest request; private HttpServletResponse response;
    @BeforeEach void setup() throws Exception {
        auth = mock(AuthService.class); plans = mock(ItineraryService.class); servlet = new ItineraryServlet(auth, plans);
        request = mock(HttpServletRequest.class); response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(auth.authenticate("token")).thenReturn(new AuthService.Account(7,"A","a@example.com",false,false));
    }
    @Test void listAlwaysUsesAuthenticatedAccount() throws Exception {
        when(request.getMethod()).thenReturn("GET"); when(plans.list(7,0)).thenReturn(List.of());
        servlet.service(request,response); verify(plans).list(7,0); verify(response).setStatus(200);
    }
    @Test void detailIsScopedAndUnauthenticatedRequestsNeverReachPlans() throws Exception {
        when(request.getPathInfo()).thenReturn("/12"); when(request.getMethod()).thenReturn("GET");
        servlet.service(request,response); verify(plans).find(7,12);
        reset(plans); clearInvocations(response); when(request.getHeader("Authorization")).thenReturn(null);
        servlet.service(request,response); verify(response).setStatus(401); verifyNoInteractions(plans);
    }
    @Test void rejectsInjectedOwnerAndPriceFields() throws Exception {
        when(request.getMethod()).thenReturn("POST"); when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"contaId\":999,\"totalCentavos\":0}")));
        servlet.service(request,response); verify(response).setStatus(400); verifyNoInteractions(plans);
    }
    @Test void rejectsFractionalCentsInsteadOfSilentlyTruncating() throws Exception {
        when(request.getMethod()).thenReturn("POST"); when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"orcamentoCentavos\":100.5}")));
        servlet.service(request,response); verify(response).setStatus(400); verifyNoInteractions(plans);
    }
}
