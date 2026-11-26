package br.com.roleout.api;
import br.com.roleout.service.*;
import br.com.roleout.model.ProfileDetailsData.Input;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
@WebServlet(urlPatterns={"/api/v1/perfil-detalhes/*","/api/v1/locais-seguidos/*"})
public final class ProfileDetailsServlet extends JsonServlet {
    private final ProfileDetailsService details=new ProfileDetailsService();
    public ProfileDetailsServlet(){super(new AuthService());}
    @Override protected int bodyLimit(){return 2850000;}
    @Override protected Reply handle(HttpServletRequest req)throws IOException {
        int account=accountId(req);String path=req.getPathInfo();boolean root=path==null||path.equals("/");
        if(req.getServletPath().equals("/api/v1/locais-seguidos")){
            if(root&&req.getMethod().equals("GET"))return new Reply(200,Map.of("itens",details.followed(account)));
            if(root||!path.matches("/[1-9][0-9]*"))throw missing();
            Boolean follow=switch(req.getMethod()){case "GET"->null;case "PUT"->true;case "DELETE"->false;default->throw method();};
            return new Reply(200,details.venue(account,Integer.parseInt(path.substring(1)),follow));
        }
        if(req.getMethod().equals("GET"))return new Reply(200,details.get(account,root?null:path.substring(1)));
        if(root&&req.getMethod().equals("PUT"))return new Reply(200,details.save(account,body(req,Input.class)));
        throw method();
    }
}
