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
    @Test void scheduleChecksUseAuthenticatedOwnerAndReadOnlyMethods() throws Exception {
        when(request.getMethod()).thenReturn("GET"); when(request.getPathInfo()).thenReturn("/12/verificar");
        servlet.service(request,response); verify(plans).check(7,12);
        reset(plans); clearInvocations(response); when(request.getPathInfo()).thenReturn("/alertas"); when(plans.alerts(7)).thenReturn(List.of());
        servlet.service(request,response); verify(plans).alerts(7);
        reset(plans); clearInvocations(response); when(request.getMethod()).thenReturn("PUT");
        servlet.service(request,response); verify(response).setStatus(405); verifyNoInteractions(plans);
        clearInvocations(response); when(request.getMethod()).thenReturn("GET"); when(request.getHeader("Authorization")).thenReturn(null);
        servlet.service(request,response); verify(response).setStatus(401); verifyNoInteractions(plans);
    }
    @Test void renameUsesAuthenticatedAccountAndRequiresSession() throws Exception {
        when(request.getPathInfo()).thenReturn("/12/nome"); when(request.getMethod()).thenReturn("PUT");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"nome\":\"Aniversário\"}")));
        servlet.service(request,response);
        verify(plans).rename(7,12,new ItineraryService.Rename("Aniversário"));
        reset(plans); clearInvocations(response); when(request.getHeader("Authorization")).thenReturn(null);
        servlet.service(request,response); verify(response).setStatus(401); verifyNoInteractions(plans);
    }
    @Test void listAlwaysUsesAuthenticatedAccount() throws Exception {
        when(request.getMethod()).thenReturn("GET"); when(plans.list(7,0)).thenReturn(List.of());
        servlet.service(request,response); verify(plans).list(7,0); verify(response).setStatus(200);
    }
    @Test void deletionUsesAuthenticatedOwnerAndRequiresSession() throws Exception {
        when(request.getPathInfo()).thenReturn("/12"); when(request.getMethod()).thenReturn("DELETE");
        servlet.service(request,response); verify(plans).delete(7,12); verify(response).setStatus(204);
        reset(plans); clearInvocations(response); when(request.getHeader("Authorization")).thenReturn(null);
        servlet.service(request,response); verify(response).setStatus(401); verifyNoInteractions(plans);
    }
    @Test void deletionCannotTargetTheWholeCollection() throws Exception {
        when(request.getMethod()).thenReturn("DELETE");
        servlet.service(request,response); verify(response).setStatus(405); verifyNoInteractions(plans);
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
