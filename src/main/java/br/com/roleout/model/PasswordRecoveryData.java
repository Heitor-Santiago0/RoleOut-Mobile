package br.com.roleout.model;
import java.time.Instant;
public interface PasswordRecoveryData {
    record Request(String email) {}
    record Reset(String codigo,String senha) {}
    record State(Instant requested,Instant window,int requests,boolean pending,int attempts,Instant next) {}
}
