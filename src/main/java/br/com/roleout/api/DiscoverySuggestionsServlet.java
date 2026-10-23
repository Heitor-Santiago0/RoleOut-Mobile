package br.com.roleout.api;

import br.com.roleout.service.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebServlet("/api/v1/sugestoes-automaticas")
public final class DiscoverySuggestionsServlet extends JsonServlet {
    private final DiscoverySuggestions suggestions;
    public DiscoverySuggestionsServlet(){this(new AuthService(),new DiscoverySuggestions());}
    public DiscoverySuggestionsServlet(AuthService auth,DiscoverySuggestions suggestions){super(auth);this.suggestions=suggestions;}
    @Override protected Reply handle(HttpServletRequest request) throws IOException {
        int account=accountId(request);
        return switch(request.getMethod()) {
            case "GET" -> new Reply(200,suggestions.get(account));
            case "PUT" -> new Reply(200,suggestions.save(account,body(request,DiscoverySuggestions.Settings.class)));
            case "POST" -> new Reply(200,suggestions.evaluate(account,body(request,DiscoverySuggestions.Context.class)));
            default -> throw method();
        };
    }
}
