package br.com.roleout.service;
import br.com.roleout.model.OpeningHours;

import br.com.roleout.api.ApiException;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.ItineraryDAO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Private plans with historical snapshots; no reservation or availability guarantee. */
public class ItineraryService implements br.com.roleout.model.ItineraryData {
    private final SqlAccess sql;
    public ItineraryService() { this(new SqlAccess()); }
    public ItineraryService(SqlAccess sql) { this.sql = sql; }

    public Detail create(int account, Input input) { return create(account,input,null); }
    public Detail copy(int account,String publication,Input input) {
        SocialProfileService.validId(publication);
        return create(account,input,publication);
    }
    private Detail create(int account,Input input,String publication) {
        if(input==null)throw new IllegalArgumentException("Informe o planejamento da cópia.");
        String city = Inputs.required(input.cidade(), 150);
        String name = Inputs.optional(input.nome(), 200);
        if (name == null) name = "Rolê em " + city;
        LocalDate date;
        try { date = LocalDate.parse(Inputs.required(input.data(), 10)); }
        catch (java.time.DateTimeException e) { throw new IllegalArgumentException("Invalid planned date."); }
        if (date.getYear() < 2000 || date.getYear() > 2100
                || input.partida() == null || !input.partida().matches("([01][0-9]|2[0-3]):[0-5][0-9]")) invalid();
        if (input.pessoas() == null || input.pessoas() < 1 || input.pessoas() > 50
                || input.duracaoMinutos() == null || input.duracaoMinutos() < 60 || input.duracaoMinutos() > 720
                || input.orcamentoCentavos() == null || input.orcamentoCentavos() < 0 || input.orcamentoCentavos() > 999999999L
                || !Set.of("person", "group").contains(input.modoOrcamento() == null ? "" : input.modoOrcamento())
                || input.paradas() == null || input.paradas().isEmpty() || input.paradas().size() > 48) invalid();
        Set<Integer> ids = new HashSet<>();
        int duration = 0;
        for (StopInput stop : input.paradas()) {
            if (stop == null || stop.roleId() == null || stop.roleId() < 1 || !ids.add(stop.roleId())
                    || stop.minutos() == null || stop.minutos() < 15 || stop.minutos() > 720 || stop.minutos() % 15 != 0) invalid();
            if (stop.horario() != null && !stop.horario().matches("([01][0-9]|2[0-3]):[0-5][0-9]")) invalid();
            duration += stop.minutos();
        }
        if (duration > input.duracaoMinutos()) throw new ApiException(400, "DURACAO_EXCEDIDA", "As paradas ultrapassam a duração do roteiro.");
        long budget = input.orcamentoCentavos() * (input.modoOrcamento().equals("person") ? input.pessoas() : 1);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                var dao = new ItineraryDAO(c);
                if (!dao.lockAccount(account)) throw missing();
                if (dao.count(account) >= 100) throw new ApiException(409, "LIMITE_ROTEIROS", "Limite de 100 roteiros salvos nesta conta.");
                br.com.roleout.model.CommunityData.Credit credit=null;
                if(publication!=null){
                    var community=new br.com.roleout.dao.CommunityDAO(c);
                    var original=new br.com.roleout.dao.PublicationDAO(c).view(publication);
                    if(original==null||community.blocked("PUBLICACAO",publication))throw new ApiException(404,"PUBLICACAO_INDISPONIVEL","A publicação original não está mais disponível.");
                    if(input.paradas().stream().noneMatch(p->original.paradas().stream().anyMatch(o->o.roleId()==p.roleId())))throw new IllegalArgumentException("Mantenha ao menos uma parada do passeio original.");
                    credit=new br.com.roleout.model.CommunityData.Credit(original.id(),original.autor().id(),original.autor().nome(),original.titulo());
                    var inherited=community.publicCredit(publication);if(inherited!=null)credit=inherited;
                }
                List<Stop> stops = new ArrayList<>();
                var snapshots = new java.util.HashMap<Integer, String>();
                var openingHours = new java.util.HashMap<Integer, List<OpeningHours.Rule>>();
                var eventEnds = new java.util.HashMap<Integer, Long>();
                long total = 0;
                for (StopInput stop : input.paradas()) {
                    var role = dao.availableStop(stop.roleId());
                    if (role == null) throw unavailable();
                    snapshots.put(stop.roleId(), SavedScheduleCheck.snapshot(role.data()==null?null:role.data().toString(),role.dataFim()==null?null:role.dataFim().toString(),role.horario(),role.horarioFim(),role.funcionamento()));
                    openingHours.put(stop.roleId(), OpeningHours.decode(role.funcionamento()));
                    LocalDate roleDate=role.data(), endDate=role.dataFim();
                    if (!city.equalsIgnoreCase(role.cidade()) || (roleDate != null && (date.isBefore(roleDate) || date.isAfter(endDate == null ? roleDate : endDate)))) throw unavailable();
                    BigDecimal value=role.valor();
                    if(value==null || value.signum()<0)throw unavailable();
                    long cents=value.setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact();
                    total=Math.addExact(total,Math.multiplyExact(cents,input.pessoas()));
                    String eventTime=role.horario();
                    if(roleDate!=null && date.isAfter(roleDate))eventTime=null;
                    if(role.horarioFim()!=null)eventEnds.put(stop.roleId(),(endDate==null?0:java.time.temporal.ChronoUnit.DAYS.between(date,endDate)*1440)+java.time.LocalTime.parse(role.horarioFim()).toSecondOfDay()/60);
                    String time=stop.horario()==null?eventTime:eventTime==null || stop.horario().compareTo(eventTime)<0?stop.horario():eventTime;
                    stops.add(new Stop(stop.roleId(),role.nome(),role.local(),stop.minutos(),cents,false,time));
                }
                int cursor = java.time.LocalTime.parse(input.partida()).toSecondOfDay() / 60;
                int departure = cursor;
                for (Stop stop : stops) {
                    if (stop.horario() != null) {
                        int fixed = java.time.LocalTime.parse(stop.horario()).toSecondOfDay() / 60;
                        if (cursor > fixed) throw new ApiException(400, "HORARIO_INCOMPATIVEL", "A ordem das paradas conflita com um horário marcado. Revise o roteiro.");
                        cursor = fixed;
                    }
                    if (!OpeningHours.fits(openingHours.get(stop.roleId()), date, cursor, cursor + stop.minutos()))
                        throw new ApiException(400, "FUNCIONAMENTO_INCOMPATIVEL", "A visita a " + stop.nome() + " fica fora dos horários de funcionamento informados. Revise o horário ou a duração.");
                    cursor += stop.minutos();
                    Long end = eventEnds.get(stop.roleId());
                    if (end != null && cursor > end)
                        throw new ApiException(400, "TERMINO_INCOMPATIVEL", "A visita a " + stop.nome() + " ultrapassa o término informado do evento. Reduza a duração ou revise as paradas.");
                }
                if (cursor - departure > input.duracaoMinutos())
                    throw new ApiException(400, "DURACAO_EXCEDIDA", "As paradas e a espera pelos horários marcados ultrapassam a duração do roteiro.");
                if (total > budget) throw new ApiException(409, "ORCAMENTO_EXCEDIDO", "O preço atual das paradas ultrapassa o orçamento. Atualize as opções.");
                int id=dao.insert(account,name,city,date,input,budget,total);
                dao.insertStops(id,stops,snapshots);
                if(credit!=null)new br.com.roleout.dao.CommunityDAO(c).credit(id,credit);
                Detail saved = find(c, account, id);
                c.commit();
                return saved;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Itinerary save failed.", e); }
    }

    public SavedScheduleCheck.Result check(int account, int id) {
        try (var c=sql.connection()) {
            c.setAutoCommit(false);
            var result=SavedScheduleCheck.check(c,account,id); c.commit(); return result;
        } catch(SQLException e) { throw new RuntimeException("Schedule check failed",e); }
    }
    public record RouteAlerts(int id, String nome, SavedScheduleCheck.Result verificacao) {}
    public List<RouteAlerts> alerts(int account) {
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            var result=new ArrayList<RouteAlerts>();
            for(var route:new ItineraryDAO(c).pendingRoutes(account)) result.add(new RouteAlerts(route.id(),route.nome(),SavedScheduleCheck.check(c,account,route.id())));
            c.commit(); return result;
        } catch(SQLException e) { throw new RuntimeException("Schedule alerts failed",e); }
    }
    public List<Summary> list(int account, int offset) {
        if (offset < 0 || offset > 100) invalid();
        try (var c = sql.connection()) { return new ItineraryDAO(c).list(account,offset);
        } catch (SQLException e) { throw new RuntimeException("Itinerary list failed.", e); }
    }
    public Detail rename(int account, int id, Rename input) {
        String name = Inputs.required(input == null ? null : input.nome(), 200);
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                var dao=new ItineraryDAO(c);
                if(!dao.lockOwned(account,id))throw missing();
                dao.rename(account,id,name);
                var result = find(c, account, id);
                c.commit(); return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Itinerary rename failed.", e); }
    }
    public void delete(int account, int id) {
        // The foreign key removes only this plan's snapshots in the same statement.
        try (var c = sql.connection()) {
            if(!new ItineraryDAO(c).delete(account,id))throw missing();
        } catch (SQLException e) { throw new RuntimeException("Itinerary deletion failed.", e); }
    }
    public Detail find(int account, int id) {
        try (var c = sql.connection()) { return find(c, account, id); }
        catch (SQLException e) { throw new RuntimeException("Itinerary lookup failed.", e); }
    }
    public Detail progress(int account, int id, int roleId, Progress input) {
        if (input == null || input.concluida() == null) invalid();
        try (var c = sql.connection()) {
            c.setAutoCommit(false);
            try {
                var dao=new ItineraryDAO(c);
                if(!dao.lockOwned(account,id) || !dao.hasStop(id,roleId))throw missing();
                dao.progress(id,roleId,input.concluida());
                var result = find(c, account, id);
                c.commit(); return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException("Itinerary progress failed.", e); }
    }
    private Detail find(Connection c, int account, int id) throws SQLException {
        var found=new ItineraryDAO(c).find(account,id);
        if(found==null)throw missing();
        return found;
    }
    private void invalid() { throw new IllegalArgumentException("Confira os campos do roteiro."); }
    private ApiException missing() { return new ApiException(404, "NAO_ENCONTRADO", "Roteiro não encontrado."); }
    private ApiException unavailable() { return new ApiException(409, "PARADA_INDISPONIVEL", "Uma parada mudou ou não está disponível para essa cidade e data. Atualize as opções."); }
}
