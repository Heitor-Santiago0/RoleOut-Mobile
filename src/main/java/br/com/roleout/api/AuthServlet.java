package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.security.AuthRateLimiter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebServlet("/api/v1/auth/*")
public final class AuthServlet extends JsonServlet {
    private final AuthRateLimiter limiter = new AuthRateLimiter();
    public AuthServlet() { this(new AuthService()); }
    public AuthServlet(AuthService auth) { super(auth); }
    @Override
    protected Reply handle(HttpServletRequest request) throws IOException {
        String path = request.getPathInfo();
        if ("/me".equals(path)) {
            if (!"GET".equals(request.getMethod())) throw method();
            return new Reply(200, auth.authenticate(token(request)));
        }
        if (!java.util.Set.of("/cadastro", "/login", "/logout").contains(path == null ? "" : path)) throw missing();
        if (!"POST".equals(request.getMethod())) throw method();
        if ("/logout".equals(path)) {
            auth.logout(token(request));
            return new Reply(204, null);
        }
        limiter.check(request.getRemoteAddr());
        if ("/cadastro".equals(path)) return new Reply(201, auth.signup(body(request, AuthService.Signup.class)));
        return new Reply(200, auth.login(body(request, AuthService.Login.class)));
    }
}
