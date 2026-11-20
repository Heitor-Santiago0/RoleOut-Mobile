package br.com.roleout.model;
import java.util.List;
import java.math.BigDecimal;
import br.com.roleout.model.OpeningHours;
import br.com.roleout.model.RoleCharacteristics;
public interface RoleDraftData {
    public record Input(String nome, String descricao, String categoria, String data,
                        BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento, RoleCharacteristics caracteristicas) {
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, cancelado, motivoCancelamento, dataFim, funcionamento, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, cancelado, motivoCancelamento, dataFim, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim, boolean cancelado, String motivoCancelamento) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, cancelado, motivoCancelamento, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario, String horarioFim) {
            this(nome, descricao, categoria, data, valor, versao, horario, horarioFim, false, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao, String horario) {
            this(nome, descricao, categoria, data, valor, versao, horario, null);
        }
        public Input(String nome, String descricao, String categoria, String data, BigDecimal valor, Integer versao) {
            this(nome, descricao, categoria, data, valor, versao, null, null);
        }
    }
    public record Draft(int id, int localId, String nome, String descricao, String categoria,
                        String data, BigDecimal valor, int versao, String status, Integer roleId, String horario, String horarioFim, boolean cancelado, String motivoCancelamento, String dataFim, List<OpeningHours.Rule> funcionamento, RoleCharacteristics caracteristicas) {}
    public record Submission(Integer versao) {}
    public record Review(int id, int versao, String decisao, String justificativa, String criadoEm, String conteudo) {}
}
