package br.com.roleout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** HTTPS transport: credentials and recovery codes never enter diagnostic messages. */
public final class BrevoRecoveryMail implements RecoveryMail {
    private final Map<String,String> env;
    private final HttpClient client;
    public BrevoRecoveryMail(Map<String,String> env) {
        this(env, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    BrevoRecoveryMail(Map<String,String> env, HttpClient client) {
        this.env=Map.copyOf(env); this.client=client;
    }
    public boolean enabled() {
        String key=env.getOrDefault("BREVO_API_KEY","");
        if(key.isBlank() || key.chars().anyMatch(c -> c<=32 || c>=127)) return false;
        try { new InternetAddress(env.getOrDefault("MAIL_FROM",""),true).validate(); return true; }
        catch(Exception e) { return false; }
    }
    public void send(String email,String code) throws Exception {
        if(!enabled()) throw new IllegalStateException("Recovery mail disabled");
        new InternetAddress(email,true).validate();
        if(code==null || !code.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid recovery code");
        var body=Map.of("sender",Map.of("email",env.get("MAIL_FROM"),"name",env.getOrDefault("MAIL_FROM_NAME","RolêOut")),
                "to",List.of(Map.of("email",email)),"subject","RolêOut - recuperar senha",
                "textContent","Você solicitou uma nova senha no RolêOut.\n\nNo aplicativo, toque em Já tenho um código e cole:\n\n"+code+
                "\n\nO código vale por 15 minutos e só pode ser usado uma vez. Use o código do e-mail mais recente. Se você não solicitou, ignore esta mensagem. Não compartilhe o código. Sua senha só muda depois da confirmação no app.");
        var request=HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                .timeout(Duration.ofSeconds(15)).header("api-key",env.get("BREVO_API_KEY"))
                .header("Content-Type","application/json").header("Accept","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(body))).build();
        try {
            // Discard provider responses: they can contain addresses and message content.
            int status=client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
            if(status!=201) throw new IOException("Recovery provider rejected delivery (HTTP "+status+")");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Recovery delivery interrupted");
        } catch(IOException e) { throw new IOException("Recovery delivery failed"); }
    }
}
