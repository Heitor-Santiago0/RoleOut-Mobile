package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.RoleDraftService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/v1/rascunhos-roles/*")
public final class RoleDraftServlet extends JsonServlet {
    @Override protected int bodyLimit() { return 65536; }
    private final RoleDraftService drafts;
    public RoleDraftServlet() { this(new AuthService(), new RoleDraftService()); }
    public RoleDraftServlet(AuthService auth, RoleDraftService drafts) { super(auth); this.drafts = drafts; }
    @Override protected Reply handle(HttpServletRequest request) throws IOException {
        int account = accountId(request);
        String path = request.getPathInfo();
        if (path == null || !path.matches("/[1-9][0-9]*(/[1-9][0-9]*(/(enviar|revisoes))?)?")) throw missing();
        String[] parts = path.substring(1).split("/");
        int venue = Integer.parseInt(parts[0]);
        if (parts.length == 3) {
            int id = Integer.parseInt(parts[1]);
            if (parts[2].equals("enviar") && request.getMethod().equals("POST")) return new Reply(200, drafts.submit(account, venue, id, body(request, RoleDraftService.Submission.class)));
            if (parts[2].equals("revisoes") && request.getMethod().equals("GET")) return new Reply(200, Map.of("itens", drafts.reviews(account, venue, id)));
            throw method();
        }
        if (parts.length == 1) {
            if (request.getMethod().equals("GET")) {
                String offset = request.getParameter("deslocamento");
                return new Reply(200, Map.of("itens", drafts.list(account, venue, offset == null ? 0 : Integer.parseInt(offset))));
            }
            if (request.getMethod().equals("POST")) return new Reply(201, drafts.save(account, venue, null, body(request, RoleDraftService.Input.class)));
        } else if (request.getMethod().equals("PUT")) {
            return new Reply(200, drafts.save(account, venue, Integer.parseInt(parts[1]), body(request, RoleDraftService.Input.class)));
        }
        throw method();
    }
}
