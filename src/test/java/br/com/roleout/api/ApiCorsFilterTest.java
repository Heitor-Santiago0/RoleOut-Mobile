package br.com.roleout.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ApiCorsFilterTest {
    @Test void nativeRequestsContinueWithoutCors() throws Exception {
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class); var chain = mock(FilterChain.class);
        new ApiCorsFilter("").doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
        verify(res, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
    }
    @Test void explicitOriginCanPreflightBearerPost() throws Exception {
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class); var chain = mock(FilterChain.class);
        when(req.getHeader("Origin")).thenReturn("http://localhost:8081");
        when(req.getMethod()).thenReturn("OPTIONS");
        when(req.getHeader("Access-Control-Request-Method")).thenReturn("POST");
        when(req.getHeader("Access-Control-Request-Headers")).thenReturn("authorization, Content-Type");
        new ApiCorsFilter("http://localhost:8081").doFilter(req, res, chain);
        verify(res).setStatus(204); verifyNoInteractions(chain);
        verify(res).setHeader("Access-Control-Allow-Origin", "http://localhost:8081");
    }
    @Test void unlistedOriginIsRejected() throws Exception {
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class); var chain = mock(FilterChain.class);
        when(req.getHeader("Origin")).thenReturn("http://localhost:8081.evil.test");
        new ApiCorsFilter("http://localhost:8081,*,null").doFilter(req, res, chain);
        verify(res).setStatus(403); verifyNoInteractions(chain);
    }
    @Test void allowedOriginStillPassesThroughAuthentication() throws Exception {
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class); var chain = mock(FilterChain.class);
        when(req.getHeader("Origin")).thenReturn("http://localhost:8081"); when(req.getMethod()).thenReturn("GET");
        new ApiCorsFilter("http://localhost:8081").doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
    }
    @Test void explicitOriginCanPreflightPutForDraftEditing() throws Exception {
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class); var chain = mock(FilterChain.class);
        when(req.getHeader("Origin")).thenReturn("http://localhost:8081");
        when(req.getMethod()).thenReturn("OPTIONS");
        when(req.getHeader("Access-Control-Request-Method")).thenReturn("PUT");
        new ApiCorsFilter("http://localhost:8081").doFilter(req, res, chain);
        verify(res).setStatus(204); verifyNoInteractions(chain);
        verify(res).setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
    }
    @Test void explicitOriginCanPreflightAuthenticatedDeletion() throws Exception {
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class); var chain = mock(FilterChain.class);
        when(req.getHeader("Origin")).thenReturn("http://localhost:8081");
        when(req.getMethod()).thenReturn("OPTIONS");
        when(req.getHeader("Access-Control-Request-Method")).thenReturn("DELETE");
        when(req.getHeader("Access-Control-Request-Headers")).thenReturn("authorization");
        new ApiCorsFilter("http://localhost:8081").doFilter(req, res, chain);
        verify(res).setStatus(204); verifyNoInteractions(chain);
    }
    @Test void unexpectedPreflightHeadersAreRejected() throws Exception {
        var req = mock(HttpServletRequest.class); var res = mock(HttpServletResponse.class); var chain = mock(FilterChain.class);
        when(req.getHeader("Origin")).thenReturn("http://localhost:8081"); when(req.getMethod()).thenReturn("OPTIONS");
        when(req.getHeader("Access-Control-Request-Method")).thenReturn("POST");
        when(req.getHeader("Access-Control-Request-Headers")).thenReturn("X-Admin");
        new ApiCorsFilter("http://localhost:8081").doFilter(req, res, chain);
        verify(res).setStatus(403); verifyNoInteractions(chain);
    }
}
