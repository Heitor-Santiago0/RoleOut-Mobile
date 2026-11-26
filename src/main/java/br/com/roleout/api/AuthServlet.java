package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.security.AuthRateLimiter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebServlet("/api/v1/auth/*")
public final class AuthServlet extends JsonServlet {
    private final AuthRateLimiter limiter = new AuthRateLimiter();
    private final br.com.roleout.service.PasswordRecoveryService recovery;
    public AuthServlet() { this(new AuthService()); }
    public AuthServlet(AuthService auth) { this(auth,new br.com.roleout.service.PasswordRecoveryService()); }
    public AuthServlet(AuthService auth,br.com.roleout.service.PasswordRecoveryService recovery) { super(auth);this.recovery=recovery; }
    @Override
    protected Reply handle(HttpServletRequest request) throws IOException {
        String path = request.getPathInfo();
        if("/recuperacao".equals(path)||"/nova-senha".equals(path)){
            if(!"POST".equals(request.getMethod()))throw method();
            limiter.check(request.getRemoteAddr());
            if("/recuperacao".equals(path)){
                recovery.request(body(request,br.com.roleout.model.PasswordRecoveryData.Request.class));
                return new Reply(202,java.util.Map.of("mensagem","Se houver uma conta com esse e-mail, enviaremos um código. Confira também o spam."));
            }
            recovery.reset(body(request,br.com.roleout.model.PasswordRecoveryData.Reset.class));
            return new Reply(204,null);
        }
        if ("/me".equals(path)) {
            if ("PUT".equals(request.getMethod())) return new Reply(200, auth.updateProfile(token(request), body(request, AuthService.ProfileInput.class)));
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
