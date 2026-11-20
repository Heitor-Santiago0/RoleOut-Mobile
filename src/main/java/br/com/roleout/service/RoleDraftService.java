package br.com.roleout.service;
import br.com.roleout.model.RoleCharacteristics;
import br.com.roleout.model.OpeningHours;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.RoleDraftDAO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Private drafts remain separate from legacy/public roles until reviewed. */
public class RoleDraftService implements br.com.roleout.model.RoleDraftData {
    private final SqlAccess sql;
    public RoleDraftService() { this(new SqlAccess()); }
    public RoleDraftService(SqlAccess sql) { this.sql = sql; }

    public List<Draft> list(int account, int venue, int offset) {
        if (offset < 0 || offset > 100000) throw new IllegalArgumentException("Página inválida.");
        try (var c = sql.connection()) {
            authorize(c, account, venue, false);
            return new RoleDraftDAO(c).list(venue,offset);
        } catch (SQLException e) { throw new RuntimeException("Draft listing failed.", e); }
    }

    public Draft save(int account, int venue, Integer id, Input input) {
        Input v = validate(input, id != null);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                authorize(c, account, venue, true);
                RoleCharacteristics characteristics = v.caracteristicas();
                if (id != null) {
                    var current = find(c, venue, id, true);
                    if (characteristics == null) characteristics = current.caracteristicas();
                    if (current.status().equals("PENDENTE")) throw conflict("Aguarde a análise antes de editar.");
                    if (v.cancelado() && current.roleId() == null) throw conflict("Somente eventos publicados podem ser cancelados.");
                    if (current.versao() != v.versao()) throw conflict("O rascunho mudou. Recarregue antes de salvar.");
                }
                if (id == null && v.cancelado()) throw conflict("Somente eventos publicados podem ser cancelados.");
                id=new RoleDraftDAO(c).save(venue,id,v,characteristics);
                if(id==0)throw conflict("O rascunho mudou. Recarregue antes de salvar.");
                var saved = find(c, venue, id, true); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Draft save failed.", e); }
    }

    public Draft submit(int account, int venue, int id, Submission input) {
        if (input == null || input.versao() == null || input.versao() < 1) throw new IllegalArgumentException("Versão inválida.");
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                authorize(c, account, venue, true);
                var current = find(c, venue, id, true); editable(current);
                if (current.versao() != input.versao()) throw conflict("O rascunho mudou. Recarregue antes de enviar.");
                new RoleDraftDAO(c).submit(id);
                var saved = find(c, venue, id, true); c.commit(); return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Draft submission failed.", e); }
    }
    public List<Review> reviews(int account, int venue, int id) {
        try (var c = sql.connection()) {
            authorize(c, account, venue, false); find(c, venue, id, false);
            return new RoleDraftDAO(c).reviews(id);
        } catch (SQLException e) { throw new RuntimeException("Draft review history failed.", e); }
    }
    private void editable(Draft draft) {
        if (!draft.status().equals("RASCUNHO") && !draft.status().equals("REJEITADO"))
            throw conflict("O rolê não pode ser alterado enquanto aguarda análise ou após publicação.");
    }
    private void authorize(Connection c, int account, int venue, boolean write) throws SQLException {
        // Keep the same organization -> venue lock order as administrative review.
        var dao=new RoleDraftDAO(c);String org=dao.organizationStatus(account,venue,write);
        if(org==null)throw missing();
        if(write){
            if(!org.equals("APROVADA"))throw conflict("A organização precisa estar aprovada.");
            String local=dao.lockVenue(venue);if(local==null)throw missing();
            if(!local.equals("PUBLICADO"))throw conflict("O local precisa estar aprovado.");
        }
    }
    private Draft find(Connection c,int venue,int id,boolean lock)throws SQLException {
        var found=new RoleDraftDAO(c).find(venue,id,lock);if(found==null)throw missing();return found;
    }
    private Input validate(Input v, boolean edit) {
        if (v == null || v.valor() == null || v.valor().signum() < 0 || v.valor().compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Informe um valor válido por pessoa.");
        BigDecimal money;
        try { money = v.valor().setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Use até duas casas decimais."); }
        if (edit ? v.versao() == null || v.versao() < 1 : v.versao() != null)
            throw new IllegalArgumentException("Versão inválida.");
        String date = Inputs.optional(v.data(), 10);
        if (date != null) {
            try {
                if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(date).getYear() < 1000)
                    throw new IllegalArgumentException("Data inválida.");
            } catch (DateTimeParseException e) { throw new IllegalArgumentException("Data inválida."); }
        }
        String time = Inputs.optional(v.horario(), 5);
        if (time != null && (date == null || !time.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))) throw new IllegalArgumentException("Informe data e horário válidos para o evento.");
        String end = Inputs.optional(v.horarioFim(), 5);
        String endDate = Inputs.optional(v.dataFim(), 10);
        if (endDate != null) {
            try {
                if (!endDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || date == null || time == null || end == null || LocalDate.parse(endDate).isBefore(LocalDate.parse(date)))
                    throw new IllegalArgumentException("Informe início e término completos, com datas em ordem.");
            } catch (DateTimeParseException e) { throw new IllegalArgumentException("Data de término inválida."); }
        }
        if (end != null && (time == null || !end.matches("([01][0-9]|2[0-3]):[0-5][0-9]") || ((endDate == null || endDate.equals(date)) && end.compareTo(time) <= 0)))
            throw new IllegalArgumentException("O término deve ser posterior ao início. Para atravessar a meia-noite, informe a data de término.");
        return new Input(Inputs.required(v.nome(), 200), Inputs.optional(v.descricao(), 4000),
                Inputs.required(v.categoria(), 100), date, money, v.versao(), time, end, v.cancelado(), v.cancelado() ? Inputs.required(v.motivoCancelamento(), 2000) : null, endDate, OpeningHours.validate(v.funcionamento()), RoleCharacteristics.validate(v.caracteristicas()));
    }
    private ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Cadastro não encontrado."); }
    private ApiException conflict(String message) { return new ApiException(409, "ESTADO_INVALIDO", message); }
}
