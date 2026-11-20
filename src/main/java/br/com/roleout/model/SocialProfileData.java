package br.com.roleout.model;
import java.util.List;
public interface SocialProfileData {
    public record Input(String nome, String apresentacao, Boolean publico, Integer versao) {}
    public record Own(String id, String nome, String apresentacao, boolean publico, int versao) {}
    public record Public(String id, String nome, String apresentacao) {}
    public record Page(List<Public> itens, String proximo) {}
}
