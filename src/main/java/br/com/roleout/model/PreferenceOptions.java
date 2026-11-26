package br.com.roleout.model;

import java.util.List;
import java.util.Map;

/** Shared vocabulary for user preferences and approved event characteristics. */
public final class PreferenceOptions {
    private PreferenceOptions() {}
    public static final Map<String, List<String>> OPTIONS = Map.of(
        "musica", List.of("Pop", "Rock", "Sertanejo", "MPB", "Samba e pagode", "Eletrônica", "Forró", "Hip-hop", "Funk", "Jazz", "Blues", "Reggae", "Clássica", "Gospel", "Indie", "K-pop"),
        "comida", List.of("Brasileira", "Italiana", "Japonesa", "Hambúrguer", "Vegetariana", "Cafés e doces", "Vegana", "Mineira", "Churrasco", "Pizza", "Mexicana", "Árabe", "Frutos do mar", "Comida de rua"),
        "atividades", List.of("Música ao vivo", "Cinema", "Museus", "Gastronomia", "Passeios ao ar livre", "Esportes", "Teatro", "Dança", "Fotografia", "História e patrimônio", "Ciência", "Feiras e artesanato", "Jogos", "Bem-estar", "Turismo religioso", "Trilhas", "Compras"),
        "ambientes", List.of("Tranquilo", "Animado", "Ao ar livre", "Familiar", "Romântico", "Cultural", "Com amigos", "Com crianças", "Pet friendly", "Conhecer pessoas", "Contato com a natureza", "Contemplativo", "Aventura", "Ao pôr do sol"));
}
