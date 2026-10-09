package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.RoleReviewService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/v1/revisoes-roles")
public final class RoleReviewServlet extends JsonServlet {
    private final RoleReviewService reviews;
    public RoleReviewServlet() { this(new AuthService(), new RoleReviewService()); }
    public RoleReviewServlet(AuthService auth, RoleReviewService reviews) { super(auth); this.reviews = reviews; }
    @Override protected Reply handle(HttpServletRequest request) throws IOException {
        var account = auth.authenticate(token(request));
        if (!account.administrador()) throw new ApiException(403, "SEM_PERMISSAO", "Acesso exclusivo de administradores.");
        return switch (request.getMethod()) {
            case "GET" -> new Reply(200, Map.of("itens", reviews.pending(account.id(), request.getParameter("deslocamento") == null ? 0 : Integer.parseInt(request.getParameter("deslocamento"))), "limite", 20));
            case "POST" -> new Reply(200, reviews.decide(account.id(), body(request, RoleReviewService.Decision.class)));
            default -> throw method();
        };
    }
}
