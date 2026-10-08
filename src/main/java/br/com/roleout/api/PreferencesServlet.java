package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.PreferencesService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebServlet("/api/v1/preferencias")
public final class PreferencesServlet extends JsonServlet {
    private final PreferencesService preferences;
    public PreferencesServlet() { this(new AuthService(), new PreferencesService()); }
    public PreferencesServlet(AuthService auth, PreferencesService preferences) { super(auth); this.preferences = preferences; }
    @Override protected Reply handle(HttpServletRequest request) throws IOException {
        int account = accountId(request);
        return switch (request.getMethod()) {
            case "GET" -> new Reply(200, preferences.get(account));
            case "POST" -> new Reply(200, preferences.save(account, body(request, PreferencesService.Input.class)));
            default -> throw method();
        };
    }
}
