package br.com.roleout.api;
import br.com.roleout.service.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/v1/comunidade/*")
public final class CommunityServlet extends JsonServlet {
    private final CommunityService community;
    private final ItineraryService itineraries;
    public CommunityServlet(){this(new AuthService(),new CommunityService(),new ItineraryService());}
    public CommunityServlet(AuthService auth,CommunityService community,ItineraryService itineraries){super(auth);this.community=community;this.itineraries=itineraries;}
    @Override protected Reply handle(HttpServletRequest req)throws IOException {
        int account=accountId(req);String path=req.getPathInfo(),method=req.getMethod();
        String raw=req.getParameter("apos");Long after=raw==null?null:Long.valueOf(raw);
        if(path==null)throw missing();
        String[] p=path.split("/");
        if(path.equals("/feed")&&method.equals("GET"))return new Reply(200,community.feed(account,"true".equals(req.getParameter("seguindo")),after));
        if(path.matches("/seguir/[0-9a-f-]{36}")){
            switch(method){
                case "GET":return new Reply(200,Map.of("seguindo",community.follows(account,p[2])));
                case "PUT":community.follow(account,p[2],true);return new Reply(204,null);
                case "DELETE":community.follow(account,p[2],false);return new Reply(204,null);
                default:throw method();
            }
        }
        if(path.matches("/copiar/[0-9a-f-]{36}")&&method.equals("POST")){
            var input=body(req,CommunityService.CopyInput.class);
            return new Reply(201,itineraries.copy(account,p[2],input.planejamento()));
        }
        if(path.matches("/creditos/[1-9][0-9]*")&&method.equals("GET")){
            var credit=community.credit(account,Integer.parseInt(p[2]));return new Reply(200,Map.of("creditos",credit==null?java.util.List.of():java.util.List.of(credit)));
        }
        if(path.matches("/origem/[0-9a-f-]{36}")&&method.equals("GET")){
            var credit=community.publicCredit(account,p[2]);return new Reply(200,Map.of("creditos",credit==null?java.util.List.of():java.util.List.of(credit)));
        }
        if(path.matches("/textos/(COMENTARIO|AVALIACAO)/[0-9a-f-]+")){
            switch(method){
                case "GET":return new Reply(200,community.entries(account,p[2],p[3],after));
                case "PUT":community.text(account,p[2],p[3],body(req,CommunityService.TextInput.class));return new Reply(204,null);
                case "DELETE":community.deleteText(account,p[2],p[3]);return new Reply(204,null);
                default:throw method();
            }
        }
        if(path.equals("/denuncias")&&method.equals("POST")){community.report(account,body(req,CommunityService.ReportInput.class));return new Reply(204,null);}
        if(path.equals("/denuncias")&&method.equals("GET"))return new Reply(200,Map.of("itens",community.reports(account,after)));
        if(path.matches("/denuncias/[1-9][0-9]*")&&method.equals("PUT")){community.decide(account,Long.parseLong(p[2]),body(req,CommunityService.Decision.class));return new Reply(204,null);}
        throw missing();
    }
}
