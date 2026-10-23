package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.SocialProfileService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebServlet(urlPatterns={"/api/v1/perfil-social","/api/v1/perfis","/api/v1/perfis/*"})
public final class SocialProfileServlet extends JsonServlet {
    private final SocialProfileService profiles;
    public SocialProfileServlet(){this(new AuthService(),new SocialProfileService());}
    public SocialProfileServlet(AuthService auth,SocialProfileService profiles){super(auth);this.profiles=profiles;}
    @Override protected Reply handle(HttpServletRequest req) throws IOException {
        int account=accountId(req);
        if("/api/v1/perfil-social".equals(req.getServletPath())) return switch(req.getMethod()) {
            case "GET" -> new Reply(200,profiles.own(account));
            case "PUT" -> new Reply(200,profiles.save(account,body(req,SocialProfileService.Input.class)));
            default -> throw method();
        };
        if(!"GET".equals(req.getMethod()))throw method();
        String path=req.getPathInfo();
        if(path==null || path.equals("/"))return new Reply(200,profiles.list(req.getParameter("apos")));
        if(!path.matches("/[0-9a-f-]{36}"))throw missing();
        return new Reply(200,profiles.view(path.substring(1)));
    }
}
