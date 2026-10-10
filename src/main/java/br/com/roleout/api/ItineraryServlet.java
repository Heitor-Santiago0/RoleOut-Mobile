package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.ItineraryService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/v1/roteiros/*")
public final class ItineraryServlet extends JsonServlet {
    private final ItineraryService itineraries;
    public ItineraryServlet() { this(new AuthService(), new ItineraryService()); }
    public ItineraryServlet(AuthService auth, ItineraryService itineraries) { super(auth); this.itineraries = itineraries; }
    @Override protected Reply handle(HttpServletRequest request) throws IOException {
        int account = accountId(request);
        String path = request.getPathInfo();
        String method = request.getMethod();
        if (path == null || path.equals("/")) {
            if (method.equals("POST")) return new Reply(201, itineraries.create(account, body(request, ItineraryService.Input.class)));
            if (method.equals("GET")) {
                String offset = request.getParameter("deslocamento");
                return new Reply(200, Map.of("itens", itineraries.list(account, offset == null ? 0 : Integer.parseInt(offset)), "limite", 20));
            }
            throw method();
        }
        if (!path.matches("/[1-9][0-9]*")) throw missing();
        if (method.equals("DELETE")) {
            itineraries.delete(account, Integer.parseInt(path.substring(1)));
            return new Reply(204, null);
        }
        if (!method.equals("GET")) throw method();
        return new Reply(200, itineraries.find(account, Integer.parseInt(path.substring(1))));
    }
}
