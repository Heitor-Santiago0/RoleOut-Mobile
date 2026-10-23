package br.com.roleout.api;

import br.com.roleout.service.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebServlet("/api/v1/notificacoes")
public final class PushServlet extends JsonServlet {
    private final PushService push;
    public PushServlet() { this(new AuthService(),new PushService()); }
    public PushServlet(AuthService auth,PushService push) { super(auth);this.push=push; }
    @Override protected Reply handle(HttpServletRequest request) throws IOException {
        int account=accountId(request);String token=token(request);
        return switch(request.getMethod()) {
            case "GET" -> new Reply(200,push.status(token));
            case "PUT" -> { push.register(account,token,body(request,PushService.Registration.class));yield new Reply(200,push.status(token)); }
            case "DELETE" -> { push.remove(token);yield new Reply(204,null); }
            default -> throw method();
        };
    }
}
