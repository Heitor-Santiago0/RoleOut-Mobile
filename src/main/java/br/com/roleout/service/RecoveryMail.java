package br.com.roleout.service;
public interface RecoveryMail {
    boolean enabled();
    void send(String email,String code)throws Exception;
}
