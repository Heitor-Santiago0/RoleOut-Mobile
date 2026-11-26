package br.com.roleout.api;
import br.com.roleout.service.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordRecoveryServletTest {
    @Test void requestUsesGenericAcceptedResponseWithoutSessionOrCode()throws Exception {
        var request=mock(HttpServletRequest.class);var response=mock(HttpServletResponse.class);var output=new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));when(request.getPathInfo()).thenReturn("/recuperacao");when(request.getMethod()).thenReturn("POST");when(request.getContentType()).thenReturn("application/json");when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"email\":\"unknown@example.com\"}")));
        var recovery=mock(PasswordRecoveryService.class);new AuthServlet(mock(AuthService.class),recovery).service(request,response);
        verify(response).setStatus(202);verify(response).setHeader("Cache-Control","no-store");assertTrue(output.toString().contains("Se houver uma conta"));assertFalse(output.toString().contains("unknown@example.com"));
        verify(recovery).request(new br.com.roleout.model.PasswordRecoveryData.Request("unknown@example.com"));
    }
    @Test void resetReturnsNoSessionAndRejectsAccountOverride()throws Exception {
        var request=mock(HttpServletRequest.class);var response=mock(HttpServletResponse.class);when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getPathInfo()).thenReturn("/nova-senha");when(request.getMethod()).thenReturn("POST");when(request.getContentType()).thenReturn("application/json");when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"codigo\":\"x\",\"senha\":\"abc123\",\"contaId\":1}")));
        var recovery=mock(PasswordRecoveryService.class);new AuthServlet(mock(AuthService.class),recovery).service(request,response);verify(response).setStatus(400);verifyNoInteractions(recovery);
    }
}
