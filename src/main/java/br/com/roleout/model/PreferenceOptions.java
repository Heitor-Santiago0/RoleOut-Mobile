package br.com.roleout.model;

import java.util.List;
import java.util.Map;

/** Shared vocabulary for user preferences and approved event characteristics. */
public final class PreferenceOptions {
    private PreferenceOptions() {}
    public static final Map<String, List<String>> OPTIONS = Map.of(
        "musica", List.of("Pop", "Rock", "Sertanejo", "MPB", "Samba e pagode", "Eletrônica", "Forró", "Hip-hop"),
        "comida", List.of("Brasileira", "Italiana", "Japonesa", "Hambúrguer", "Vegetariana", "Cafés e doces"),
        "atividades", List.of("Música ao vivo", "Cinema", "Museus", "Gastronomia", "Passeios ao ar livre", "Esportes"),
        "ambientes", List.of("Tranquilo", "Animado", "Ao ar livre", "Familiar", "Romântico", "Cultural"));
}
