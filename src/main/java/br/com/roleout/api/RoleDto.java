package br.com.roleout.api;

import br.com.roleout.model.Role;
import java.math.BigDecimal;

public record RoleDto(int id, String nome, String descricao, String local,
                      String cidade, String data, String categoria, BigDecimal valor) {
    public static RoleDto from(Role role) {
        return new RoleDto(role.getId(), role.getNome(), role.getDescricao(), role.getLocal(),
                role.getCidade(), role.getData() == null ? null : role.getData().toString(),
                role.getCategoria(), BigDecimal.valueOf(role.getValor()));
    }
}
