package br.com.roleout.model;
import java.util.List;
public interface DiscoveryData {
    public record Settings(Boolean ativo, Boolean viagens, Integer intervaloHoras, String cidadeBase) {}
    public record Context(Double latitude, Double longitude, String cidade, String fuso) {}
    public record Suggestion(int roleId, String nome, String cidade, double distanciaKm, List<String> motivos, boolean outraCidade) {}
    public record Result(Suggestion sugestao) {}
}
