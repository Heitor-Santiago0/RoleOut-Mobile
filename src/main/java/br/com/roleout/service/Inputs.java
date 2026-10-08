package br.com.roleout.service;

import java.util.Locale;

public final class Inputs {
    private Inputs() { }
    public static String required(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException("Preencha os campos obrigatórios respeitando seus limites.");
        }
        return value.trim();
    }
    public static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return required(value, max);
    }
    public static String email(String value) {
        String email = required(value, 254).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        return email;
    }
    public static String password(String value) {
        if (value == null || value.length() < 12 || value.length() > 128) {
            throw new IllegalArgumentException("A senha deve ter entre 12 e 128 caracteres.");
        }
        return value;
    }
}
