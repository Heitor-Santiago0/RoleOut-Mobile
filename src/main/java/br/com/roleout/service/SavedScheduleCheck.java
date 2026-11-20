package br.com.roleout.service;
import br.com.roleout.model.OpeningHours;
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
    public static String snapshot(String date,String end,String time,String endTime,String hours) {
        try { return JSON.writeValueAsString(new Snapshot(date,end,time,endTime,Optional.ofNullable(OpeningHours.decode(hours)).orElse(List.of()))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    public static Result check(Connection c, int account, int id) throws SQLException {
        var dao=new br.com.roleout.dao.ScheduleDAO(c);
        var header=dao.header(account,id);
        if(header==null)throw new ApiException(404,"NAO_ENCONTRADO","Roteiro não encontrado.");
        LocalDate day=header.day();int cursor=minute(header.departure());String city=header.city();
        var alerts=new ArrayList<Alert>();int checked=0;
        for(var stop:dao.stops(id)) {
                String time=stop.time(); if(time!=null) cursor=Math.max(cursor,minute(time));
                int start=cursor; cursor+=stop.minutes();
                if(stop.done()) continue;
                checked++; int role=stop.roleId(); String name=stop.name();
                if(!stop.exists() || !stop.available()) { alerts.add(new Alert(role,name,"INDISPONIVEL","O cadastro não está mais disponível. Confirme esta parada.")); continue; }
                if(stop.cancelled()) { alerts.add(new Alert(role,name,"CANCELADO","Este evento foi cancelado. Revise esta parada.")); continue; }
                String original=stop.original();
                var hours=OpeningHours.decode(stop.hours());
                String current;
                try { current=JSON.writeValueAsString(new Snapshot((stop.date()==null?null:stop.date().toString()),(stop.endDate()==null?null:stop.endDate().toString()),stop.currentTime(),stop.endTime(),Optional.ofNullable(hours).orElse(List.of()))); }
                catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
                if(original==null) alerts.add(new Alert(role,name,"SEM_HISTORICO","Roteiro antigo: os horários originais não foram registrados. A compatibilidade abaixo usa o cadastro atual."));
                else if(!original.equals(current)) alerts.add(new Alert(role,name,"HORARIOS_ALTERADOS","As datas ou os horários publicados mudaram desde o salvamento. Consulte os detalhes atuais antes de sair."));
                LocalDate arrival=day.plusDays(start/1440);
                var eventDate=stop.date(); var endDate=stop.endDate();
                boolean incompatible=!city.equalsIgnoreCase(stop.city());
                if(eventDate!=null) incompatible |= arrival.isBefore(eventDate) || arrival.isAfter(endDate==null?eventDate:endDate);
                String end=stop.endTime(), begin=stop.currentTime();
                if(end!=null) {
                    LocalDate ending=endDate!=null?endDate:eventDate!=null?eventDate:day;
                    incompatible |= cursor > ChronoUnit.DAYS.between(day,ending)*1440+minute(end);
                }
                if(begin!=null && (eventDate==null || arrival.equals(eventDate))) incompatible |= start%1440>minute(begin);
                incompatible |= !OpeningHours.fits(hours,day,start,cursor);
                if(incompatible) alerts.add(new Alert(role,name,"HORARIO_INCOMPATIVEL","A visita planejada conflita com a data, o início, o término ou o funcionamento atual. Revise a parada."));
        }
        return new Result(Instant.now().toString(),List.copyOf(alerts),checked);
    }
    private static int minute(String time) { return LocalTime.parse(time).toSecondOfDay()/60; }
}
