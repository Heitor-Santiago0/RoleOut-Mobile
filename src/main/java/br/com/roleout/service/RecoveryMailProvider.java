package br.com.roleout.service;
import java.util.Map;

public final class RecoveryMailProvider {
    private RecoveryMailProvider() {}
    public static RecoveryMail configured(Map<String,String> env) {
        return switch(env.getOrDefault("MAIL_PROVIDER","smtp")) {
            case "brevo" -> new BrevoRecoveryMail(env);
            case "smtp" -> new SmtpRecoveryMail(env);
            default -> new RecoveryMail() {
                public boolean enabled(){return false;}
                public void send(String email,String code){throw new IllegalStateException("Unknown recovery provider");}
            };
        };
    }
}
