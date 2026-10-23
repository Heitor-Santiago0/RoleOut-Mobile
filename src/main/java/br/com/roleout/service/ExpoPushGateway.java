package br.com.roleout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Only fixed Expo endpoints; neither tokens nor provider error bodies are logged. */
public final class ExpoPushGateway implements PushGateway {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode post(String action, Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("https://exp.host/--/api/v2/push/"+action))
            .timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        String access=System.getenv("EXPO_ACCESS_TOKEN");
        if(access!=null && !access.isBlank()) builder.header("Authorization","Bearer "+access);
        var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==429 || response.statusCode()>=500) throw new java.io.IOException("Push temporarily unavailable");
        if(response.statusCode()!=200) return json.valueToTree(Map.of("status","error","details",Map.of("error","HTTP_"+response.statusCode())));
        JsonNode root=json.readTree(response.body());
        if(!root.has("data")) throw new java.io.IOException("Invalid push response");
        return root.get("data");
    }
    public Result send(String token,int itinerary) throws Exception {
        return parse(post("send",Map.of("to",token,"title","Atualização no seu rolê",
            "body","Uma parada teve informações alteradas. Abra o RolêOut para conferir antes de sair.",
            "data",Map.of("tipo","horarios","roteiroId",itinerary),"sound","default","channelId","horarios","ttl",3600)),true);
    }
    public Result receipt(String ticket) throws Exception {
        var data=post("getReceipts",Map.of("ids",List.of(ticket)));
        if(data.has("status")) return parse(data,false);
        return data.has(ticket)?parse(data.get(ticket),false):new Result("waiting",null,null);
    }
    static Result parse(JsonNode data,boolean needsTicket) throws java.io.IOException {
        String status=data.path("status").asText();
        if(status.equals("ok")) {
            String id=data.path("id").asText(null);
            if(needsTicket && (id==null || id.length()>200)) throw new java.io.IOException("Missing push ticket");
            return new Result("ok",id,null);
        }
        if(status.equals("error")) {
            String error=data.path("details").path("error").asText("ProviderError");
            if(!error.matches("[A-Za-z0-9_]{1,80}")) error="ProviderError";
            return new Result("error",null,error);
        }
        throw new java.io.IOException("Invalid push status");
    }
}
