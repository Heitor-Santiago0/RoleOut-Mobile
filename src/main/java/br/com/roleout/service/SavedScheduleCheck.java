package br.com.roleout.service;

import br.com.roleout.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class SavedScheduleCheck {
    public record Alert(int roleId, String nome, String codigo, String mensagem) {}
    public record Result(String verificadoEm, List<Alert> alertas, int paradasVerificadas) {}
    private record Snapshot(String data, String dataFim, String horario, String horarioFim, List<OpeningHours.Rule> funcionamento) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    public static String snapshot(ResultSet rs) throws SQLException {
        try { return JSON.writeValueAsString(new Snapshot(rs.getString("data"), rs.getString("data_fim"), rs.getString("horario"), rs.getString("horario_fim"), Optional.ofNullable(OpeningHours.decode(rs.getString("funcionamento"))).orElse(List.of()))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    public static Result check(Connection c, int account, int id) throws SQLException {
        LocalDate day; int cursor; String city;
        try (var s=c.prepareStatement("SELECT data_planejada,partida,cidade FROM roteiros WHERE id=? AND conta_id=?")) {
            s.setInt(1,id); s.setInt(2,account);
            try(var rs=s.executeQuery()) {
                if(!rs.next()) throw new ApiException(404,"NAO_ENCONTRADO","Roteiro não encontrado.");
                day=rs.getDate(1).toLocalDate(); cursor=minute(rs.getString(2)); city=rs.getString(3);
            }
        }
        var alerts=new ArrayList<Alert>(); int checked=0;
        String query="""
            SELECT p.*,r.id AS atual_id,r.data,r.data_fim,r.horario AS inicio_atual,r.horario_fim,r.funcionamento,r.cancelado,r.cidade,
              (r.local_id IS NULL OR EXISTS (SELECT 1 FROM locais l JOIN organizacoes o ON o.id=l.organizacao_id
                WHERE l.id=r.local_id AND l.status='PUBLICADO' AND o.status='APROVADA')) AS disponivel
            FROM roteiro_paradas p LEFT JOIN roles r ON r.id=p.role_id WHERE p.roteiro_id=? ORDER BY p.ordem
            """;
        try(var s=c.prepareStatement(query)) {
            s.setInt(1,id);
            try(var rs=s.executeQuery()) { while(rs.next()) {
                String time=rs.getString("horario"); if(time!=null) cursor=Math.max(cursor,minute(time));
                int start=cursor; cursor+=rs.getInt("minutos");
                if(rs.getBoolean("concluida")) continue;
                checked++; int role=rs.getInt("role_id"); String name=rs.getString("nome");
                if(rs.getObject("atual_id")==null || !rs.getBoolean("disponivel")) { alerts.add(new Alert(role,name,"INDISPONIVEL","O cadastro não está mais disponível. Confirme esta parada.")); continue; }
                if(rs.getBoolean("cancelado")) { alerts.add(new Alert(role,name,"CANCELADO","Este evento foi cancelado. Revise esta parada.")); continue; }
                String original=rs.getString("horarios_originais");
                var hours=OpeningHours.decode(rs.getString("funcionamento"));
                String current;
                try { current=JSON.writeValueAsString(new Snapshot(rs.getString("data"),rs.getString("data_fim"),rs.getString("inicio_atual"),rs.getString("horario_fim"),Optional.ofNullable(hours).orElse(List.of()))); }
                catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
                if(original==null) alerts.add(new Alert(role,name,"SEM_HISTORICO","Roteiro antigo: os horários originais não foram registrados. A compatibilidade abaixo usa o cadastro atual."));
                else if(!original.equals(current)) alerts.add(new Alert(role,name,"HORARIOS_ALTERADOS","As datas ou os horários publicados mudaram desde o salvamento. Consulte os detalhes atuais antes de sair."));
                LocalDate arrival=day.plusDays(start/1440);
                var eventDate=rs.getDate("data"); var endDate=rs.getDate("data_fim");
                boolean incompatible=!city.equalsIgnoreCase(rs.getString("cidade"));
                if(eventDate!=null) incompatible |= arrival.isBefore(eventDate.toLocalDate()) || arrival.isAfter(endDate==null?eventDate.toLocalDate():endDate.toLocalDate());
                String end=rs.getString("horario_fim"), begin=rs.getString("inicio_atual");
                if(end!=null) {
                    LocalDate ending=endDate!=null?endDate.toLocalDate():eventDate!=null?eventDate.toLocalDate():day;
                    incompatible |= cursor > ChronoUnit.DAYS.between(day,ending)*1440+minute(end);
                }
                if(begin!=null && (eventDate==null || arrival.equals(eventDate.toLocalDate()))) incompatible |= start%1440>minute(begin);
                incompatible |= !OpeningHours.fits(hours,day,start,cursor);
                if(incompatible) alerts.add(new Alert(role,name,"HORARIO_INCOMPATIVEL","A visita planejada conflita com a data, o início, o término ou o funcionamento atual. Revise a parada."));
            } }
        }
        return new Result(Instant.now().toString(),List.copyOf(alerts),checked);
    }
    private static int minute(String time) { return LocalTime.parse(time).toSecondOfDay()/60; }
}
