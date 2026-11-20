package br.com.roleout.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.HashSet;

/** Declared by the owner and only exposed publicly after approval. */
public record RoleCharacteristics(List<String> musica, List<String> comida, List<String> ambientes) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static RoleCharacteristics validate(RoleCharacteristics value) {
        return value == null ? null : new RoleCharacteristics(selection("musica", value.musica()), selection("comida", value.comida()), selection("ambientes", value.ambientes()));
    }
    private static List<String> selection(String key, List<String> values) {
        if (values == null) return List.of();
        var allowed = PreferenceOptions.OPTIONS.get(key);
        if (values.size() > allowed.size() || new HashSet<>(values).size() != values.size()
                || values.stream().anyMatch(v -> v == null || !allowed.contains(v)))
            throw new IllegalArgumentException("Confira as características do rolê: selecione opções válidas, sem repetir.");
        return List.copyOf(values);
    }
    public static String encode(RoleCharacteristics value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); }
        catch (java.io.IOException e) { throw new IllegalStateException("Characteristics encoding failed.", e); }
    }
    public static RoleCharacteristics decode(String value) {
        if (value == null) return null;
        try { return validate(JSON.readValue(value, RoleCharacteristics.class)); }
        catch (java.io.IOException e) { throw new IllegalStateException("Characteristics decoding failed.", e); }
    }
    public static String describe(RoleCharacteristics value) {
        if (value == null) return "Não informado";
        return "Música: " + names(value.musica()) + "; Culinária: " + names(value.comida()) + "; Ambiente: " + names(value.ambientes());
    }
    private static String names(List<String> values) { return values == null || values.isEmpty() ? "Não informado" : String.join(", ", values); }
}
