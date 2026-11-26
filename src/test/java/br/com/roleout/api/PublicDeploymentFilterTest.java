package br.com.roleout.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class PublicDeploymentFilterTest {
    @Test void publicImageBlocksLegacyCrudAndJsp() throws Exception {
        for (String path : new String[]{"/roles", "/sugestoes", "/index.jsp", "/", "/api/v10/roles"}) {
            var request = mock(HttpServletRequest.class);
            var response = mock(HttpServletResponse.class);
            var chain = mock(FilterChain.class);
            when(request.getServletPath()).thenReturn(path);
            new PublicDeploymentFilter(true).doFilter(request, response, chain);
            verify(response).setStatus(404);
            verifyNoInteractions(chain);
        }
    }
    @Test void preservesApiAndLocalClassroomRoutes() throws Exception {
        for (boolean deployed : new boolean[]{true, false}) {
            var request = mock(HttpServletRequest.class);
            var response = mock(HttpServletResponse.class);
            var chain = mock(FilterChain.class);
            when(request.getServletPath()).thenReturn(deployed ? "/api/v1/roles" : "/roles");
            new PublicDeploymentFilter(deployed).doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
            verifyNoInteractions(response);
        }
    }
}
