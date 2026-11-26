package br.com.roleout.model;
import java.util.List;
public interface ProfileDetailsData {
    record Input(String fotoBase64, Boolean interessesPublicos) {}
    record Details(String fotoBase64, boolean interessesPublicos, List<String> interesses, int publicacoes, int seguidores, int seguindo) {}
    record Venue(int id, String nome, Integer roleId, boolean seguindo) {}
}
