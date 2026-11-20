package br.com.roleout.model;
import java.util.List;
import br.com.roleout.model.OpeningHours;
import br.com.roleout.model.RoleCharacteristics;
public interface RoleReviewData {
    public record Published(String nome, String descricao, String categoria, String data, java.math.BigDecimal valor,
                            String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento, RoleCharacteristics caracteristicas) {}
    public record Item(RoleDraftData.Draft role, OrganizationData.Organization organizacao, OrganizationData.Venue local, Published publicacao) {}
    public record Decision(Integer rascunhoId, Integer versao, String decisao, String justificativa) {}
}
