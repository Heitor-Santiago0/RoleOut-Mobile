package br.com.roleout.api;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** The original classroom JSP CRUD must never be exposed by the public API image. */
@WebFilter("/*")
public final class PublicDeploymentFilter implements Filter {
    private final boolean publicDeployment;
    public PublicDeploymentFilter() {
        this("true".equalsIgnoreCase(System.getenv("ROLEOUT_PUBLIC_DEPLOYMENT")));
    }
    PublicDeploymentFilter(boolean publicDeployment) { this.publicDeployment = publicDeployment; }
    @Override public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) req;
        var response = (HttpServletResponse) res;
        // servletPath is decoded by the container, unlike raw requestURI.
        if (publicDeployment && !request.getServletPath().startsWith("/api/v1/")) {
            response.setStatus(404);
            return;
        }
        chain.doFilter(req, res);
    }
}
