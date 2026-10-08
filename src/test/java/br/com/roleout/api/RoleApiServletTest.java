package br.com.roleout.api;

import br.com.roleout.model.Role;
import br.com.roleout.service.RoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleApiServletTest {
    private RoleService service;
    private RoleApiServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        service = mock(RoleService.class);
        servlet = new RoleApiServlet(service);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getServletPath()).thenReturn("/api/v1/roles");
    }

    @Test
    void listsJsonWithDefaultPagination() throws Exception {
        Role role = new Role(1, "Museu", "Arte", "Centro", "Uberaba",
                LocalDate.of(2026, 10, 8), "Cultura", 12.5);
        when(service.consultarPagina(null, null, null, 20, 0)).thenReturn(List.of(role));
        servlet.doGet(request, response);
        verify(response).setStatus(200);
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        var payload = json.readTree(body.toString());
        assertEquals("2026-10-08", payload.at("/itens/0/data").asText());
        assertEquals(12.5, payload.at("/itens/0/valor").asDouble());
        assertFalse(payload.at("/itens/0").has("dataFormatada"));
        assertEquals(20, payload.get("limite").asInt());
    }

    @Test
    void detailNotFoundReturns404() throws Exception {
        when(request.getPathInfo()).thenReturn("/123");
        servlet.doGet(request, response);
        verify(response).setStatus(404);
        assertEquals("NAO_ENCONTRADO", json.readTree(body.toString()).get("codigo").asText());
    }

    @Test
    void invalidIdsAndNumericParametersReturn400() throws Exception {
        when(request.getPathInfo()).thenReturn("/-1");
        servlet.doGet(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void overflowingIdReturns400() throws Exception {
        when(request.getPathInfo()).thenReturn("/9999999999999999999999");
        servlet.doGet(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void missingSuggestionFiltersReturn400() throws Exception {
        when(request.getServletPath()).thenReturn("/api/v1/sugestoes");
        servlet.doGet(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void suggestionsForwardFiltersAndPagination() throws Exception {
        when(request.getServletPath()).thenReturn("/api/v1/sugestoes");
        when(request.getParameter("categoria")).thenReturn("Cultura");
        when(request.getParameter("cidade")).thenReturn("Uberaba");
        when(request.getParameter("valorMaximo")).thenReturn("50");
        when(request.getParameter("limite")).thenReturn("5");
        when(request.getParameter("deslocamento")).thenReturn("10");
        when(service.consultarPagina("Cultura", "Uberaba", 50.0, 5, 10)).thenReturn(List.of());
        servlet.doGet(request, response);
        verify(response).setStatus(200);
        verify(service).consultarPagina("Cultura", "Uberaba", 50.0, 5, 10);
    }

    @Test
    void nonNumericPaginationReturns400() throws Exception {
        when(request.getParameter("limite")).thenReturn("abc");
        servlet.doGet(request, response);
        verify(response).setStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void databaseFailureReturns503WithoutSqlDetails() throws Exception {
        when(service.consultarPagina(null, null, null, 20, 0)).thenThrow(
                new RuntimeException("Internal DAO details", new SQLException("secret SQL jdbc:mysql://private")));
        servlet.doGet(request, response);
        verify(response).setStatus(503);
        verify(response).setHeader("Retry-After", "5");
        assertFalse(body.toString().contains("secret"));
        assertFalse(body.toString().contains("jdbc"));
    }

    @Test
    void unexpectedFailureReturns500() throws Exception {
        when(service.consultarPagina(null, null, null, 20, 0)).thenThrow(new IllegalStateException("private details"));
        servlet.doGet(request, response);
        verify(response).setStatus(500);
        assertFalse(body.toString().contains("private"));
    }
}
