package br.com.roleout.service;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.*;

/** Provider-independent SMTP. Cleartext is restricted to the explicitly enabled local sink. */
public final class SmtpRecoveryMail implements RecoveryMail {
    private final Map<String,String> env;
    public SmtpRecoveryMail(){this(System.getenv());}
    public SmtpRecoveryMail(Map<String,String> env){this.env=Map.copyOf(env);}
    public boolean enabled(){
        if(env.getOrDefault("MAIL_HOST","").isBlank()||env.getOrDefault("MAIL_FROM","").isBlank())return false;
        try{properties();new InternetAddress(env.get("MAIL_FROM"),true).validate();return true;}
        catch(Exception e){return false;}
    }
    public Properties properties(){
        var p=new Properties();String host=env.getOrDefault("MAIL_HOST","");
        boolean local="true".equals(env.get("MAIL_LOCAL_TEST"));
        if(local&&("true".equals(env.get("ROLEOUT_PUBLIC_DEPLOYMENT"))||!Set.of("mailpit","localhost","127.0.0.1").contains(host)))throw new IllegalArgumentException("Local mail sink is not allowed here");
        String mode=env.getOrDefault("MAIL_TLS","starttls");
        if(!local&&!Set.of("starttls","ssl").contains(mode))throw new IllegalArgumentException("TLS required");
        if(!local&&(env.getOrDefault("MAIL_USER","").isBlank()||env.getOrDefault("MAIL_PASSWORD","").isBlank()))throw new IllegalArgumentException("SMTP credentials required");
        p.setProperty("mail.smtp.host",host);p.setProperty("mail.smtp.port",env.getOrDefault("MAIL_PORT",local?"1025":mode.equals("ssl")?"465":"587"));
        p.setProperty("mail.smtp.auth",String.valueOf(!local));p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.ssl.enable",String.valueOf(!local&&mode.equals("ssl")));
        p.setProperty("mail.smtp.starttls.enable",String.valueOf(!local&&mode.equals("starttls")));
        p.setProperty("mail.smtp.starttls.required",String.valueOf(!local&&mode.equals("starttls")));
        for(String timeout:List.of("connectiontimeout","timeout","writetimeout"))p.setProperty("mail.smtp."+timeout,"10000");
        return p;
    }
    public void send(String email,String code)throws Exception {
        if(!enabled())throw new IllegalStateException("Recovery mail disabled");
        var session=Session.getInstance(properties());
        var message=new MimeMessage(session);message.setFrom(new InternetAddress(env.get("MAIL_FROM"),true));
        message.setRecipient(Message.RecipientType.TO,new InternetAddress(email,true));
        message.setSubject("RolêOut - recuperar senha","UTF-8");
        message.setText("Você solicitou uma nova senha no RolêOut.\n\nNo aplicativo, toque em Já tenho um código e cole:\n\n"+code+"\n\nO código vale por 15 minutos e só pode ser usado uma vez. Use o código do e-mail mais recente. Se você não solicitou, ignore esta mensagem. Não compartilhe o código. Sua senha só muda depois da confirmação no app.","UTF-8");
        try(var transport=session.getTransport("smtp")){
            if("true".equals(env.get("MAIL_LOCAL_TEST")))transport.connect();
            else transport.connect(env.get("MAIL_USER"),env.get("MAIL_PASSWORD"));
            transport.sendMessage(message,message.getAllRecipients());
        }
    }
}
