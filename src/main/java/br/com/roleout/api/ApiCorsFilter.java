package br.com.roleout.api;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit browser origins only. Bearer authentication remains mandatory on private endpoints. */
@WebFilter("/api/v1/*")
public final class ApiCorsFilter implements Filter {
    private final Set<String> origins;
    public ApiCorsFilter() { this(System.getenv().getOrDefault("API_CORS_ORIGINS", "")); }
    ApiCorsFilter(String configured) {
        origins = Arrays.stream(configured.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("*") && !s.equals("null"))
                .collect(Collectors.toUnmodifiableSet());
    }
    @Override public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) req;
        var response = (HttpServletResponse) res;
        response.addHeader("Vary", "Origin");
        String origin = request.getHeader("Origin");
        if (origin == null) { chain.doFilter(req, res); return; }
        if (!origins.contains(origin)) { response.setStatus(403); return; }
        response.setHeader("Access-Control-Allow-Origin", origin);
        if (request.getMethod().equals("OPTIONS")) {
            String method = request.getHeader("Access-Control-Request-Method");
            String headers = request.getHeader("Access-Control-Request-Headers");
            boolean validHeaders = headers == null || Arrays.stream(headers.split(","))
                    .allMatch(h -> Set.of("authorization", "content-type", "accept").contains(h.trim().toLowerCase(java.util.Locale.ROOT)));
            if (!Set.of("GET", "POST").contains(method == null ? "" : method) || !validHeaders) {
                response.setStatus(403); return;
            }
            response.setHeader("Access-Control-Allow-Methods", "GET, POST");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
            response.setStatus(204);
            return;
        }
        chain.doFilter(req, res);
    }
}
