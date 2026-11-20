package br.com.roleout.model;
public interface AuthData {
    public record Signup(String nome, String email, String senha) { }
    public record Login(String email, String senha) { }
    public record ProfileInput(String nome) { }
    public record Account(int id, String nome, String email, boolean emailConfirmado, boolean administrador) { }
    public record Session(String token, String expiraEm, Account conta) { }
}
