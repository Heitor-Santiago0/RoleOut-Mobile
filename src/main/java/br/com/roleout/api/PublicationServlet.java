package br.com.roleout.api;

import br.com.roleout.service.AuthService;
import br.com.roleout.service.PublicationService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebServlet(urlPatterns={"/api/v1/minhas-publicacoes/*","/api/v1/publicacoes","/api/v1/publicacoes/*"})
public final class PublicationServlet extends JsonServlet {
    private final PublicationService publications;
    public PublicationServlet(){this(new AuthService(),new PublicationService());}
    public PublicationServlet(AuthService auth,PublicationService publications){super(auth);this.publications=publications;}
    @Override protected int bodyLimit(){return 8500000;}
    @Override protected Reply handle(HttpServletRequest req) throws IOException {
        int account=accountId(req);String path=req.getPathInfo();
        if("/api/v1/minhas-publicacoes".equals(req.getServletPath())) {
            if(path==null||!path.matches("/[1-9][0-9]*(/ocultar)?"))throw missing();
            int route=Integer.parseInt(path.split("/")[1]);
            if(path.endsWith("/ocultar")) {
                if(!"PUT".equals(req.getMethod()))throw method();
                return new Reply(200,publications.hide(account,route,body(req,PublicationService.Version.class)));
            }
            return switch(req.getMethod()) {
                case "GET" -> new Reply(200,publications.own(account,route));
                case "PUT" -> new Reply(200,publications.save(account,route,body(req,PublicationService.Input.class)));
                case "DELETE" -> { publications.delete(account,route,body(req,PublicationService.Version.class));yield new Reply(204,null); }
                default -> throw method();
            };
        }
        if(!"GET".equals(req.getMethod()))throw method();
        if(path==null||path.equals("/"))return new Reply(200,publications.list(req.getParameter("autor"),req.getParameter("apos")));
        if(!path.matches("/[0-9a-f-]{36}"))throw missing();
        return new Reply(200,publications.view(path.substring(1)));
    }
}
