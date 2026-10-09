package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.OrganizationService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/v1/organizacoes/*")
public final class OrganizationServlet extends JsonServlet {
    private final OrganizationService organizations;
    public OrganizationServlet() { this(new AuthService(), new OrganizationService()); }
    public OrganizationServlet(AuthService auth, OrganizationService organizations) {
        super(auth); this.organizations = organizations;
    }
    @Override
    protected Reply handle(HttpServletRequest request) throws IOException {
        int account = accountId(request);
        String path = request.getPathInfo();
        String method = request.getMethod();
        if (path == null || path.equals("/")) {
            if (method.equals("GET")) return new Reply(200, Map.of("itens", organizations.list(account)));
            if (method.equals("POST")) return new Reply(201, organizations.create(account, body(request, OrganizationService.OrganizationInput.class)));
            throw method();
        }
        if (!path.matches("/[1-9][0-9]*(/revisoes|/locais(/[1-9][0-9]*(/enviar)?)?)?")) throw missing();
        String[] parts = path.substring(1).split("/");
        int org = Integer.parseInt(parts[0]);
        if (parts.length == 2 && parts[1].equals("revisoes")) {
            if (!method.equals("GET")) throw method();
            return new Reply(200, Map.of("itens", organizations.reviews(account, org)));
        }
        if (parts.length == 4) {
            if (!method.equals("POST")) throw method();
            return new Reply(200, organizations.submitVenue(account, org, Integer.parseInt(parts[2])));
        }
        if (parts.length == 1) {
            if (method.equals("GET")) return new Reply(200, organizations.find(account, org));
            if (method.equals("PUT")) return new Reply(200, organizations.update(account, org, body(request, OrganizationService.OrganizationInput.class)));
            throw method();
        }
        if (parts.length == 2) {
            if (method.equals("GET")) return new Reply(200, Map.of("itens", organizations.listVenues(account, org)));
            if (method.equals("POST")) return new Reply(201, organizations.saveVenue(account, org, null, body(request, OrganizationService.VenueInput.class)));
            throw method();
        }
        int venue = Integer.parseInt(parts[2]);
        if (method.equals("GET")) return new Reply(200, organizations.findVenue(account, org, venue));
        if (method.equals("PUT")) return new Reply(200, organizations.saveVenue(account, org, venue, body(request, OrganizationService.VenueInput.class)));
        throw method();
    }
}
