package br.com.roleout.api;

import br.com.roleout.model.Role;
import java.math.BigDecimal;

public record RoleDto(int id, String nome, String descricao, String local,
                      String cidade, String data, String categoria, BigDecimal valor, BigDecimal latitude, BigDecimal longitude, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, java.util.List<br.com.roleout.model.OpeningHours.Rule> funcionamento, br.com.roleout.model.RoleCharacteristics caracteristicas) {
    public static RoleDto from(Role role) {
        return new RoleDto(role.getId(), role.getNome(), role.getDescricao(), role.getLocal(),
                role.getCidade(), role.getData() == null ? null : role.getData().toString(),
                role.getCategoria(), BigDecimal.valueOf(role.getValor()), role.getLatitude(), role.getLongitude(), role.getHorario(), role.getHorarioFim(), role.isCancelado(), role.getMotivoCancelamento(), role.getDataFim() == null ? null : role.getDataFim().toString(), role.getFuncionamento(), role.getCaracteristicas());
    }
}
