package br.com.roleout.dao;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class ScheduleDAO {
    public record Header(LocalDate day,String departure,String city) {}
    public record Stop(int roleId,String name,String time,int minutes,boolean done,boolean exists,boolean available,boolean cancelled,
        String original,String hours,LocalDate date,LocalDate endDate,String currentTime,String endTime,String city) {}
    private final Connection c;
    public ScheduleDAO(Connection c){this.c=c;}
    public Header header(int account,int id) throws SQLException {
        try(var s=c.prepareStatement("SELECT data_planejada,partida,cidade FROM roteiros WHERE id=? AND conta_id=?")) {
            s.setInt(1,id);s.setInt(2,account);try(var r=s.executeQuery()){return r.next()?new Header(r.getDate(1).toLocalDate(),r.getString(2),r.getString(3)):null;}
        }
    }
    public List<Stop> stops(int id) throws SQLException {
        String query="""
            SELECT p.*,r.id AS atual_id,r.data,r.data_fim,r.horario AS inicio_atual,r.horario_fim,r.funcionamento,r.cancelado,r.cidade,
              (r.local_id IS NULL OR EXISTS (SELECT 1 FROM locais l JOIN organizacoes o ON o.id=l.organizacao_id
                WHERE l.id=r.local_id AND l.status='PUBLICADO' AND o.status='APROVADA')) AS disponivel
            FROM roteiro_paradas p LEFT JOIN roles r ON r.id=p.role_id WHERE p.roteiro_id=? ORDER BY p.ordem
            """;
        var items=new ArrayList<Stop>();
        try(var s=c.prepareStatement(query)){s.setInt(1,id);try(var r=s.executeQuery()){while(r.next())items.add(new Stop(
            r.getInt("role_id"),r.getString("nome"),r.getString("horario"),r.getInt("minutos"),r.getBoolean("concluida"),r.getObject("atual_id")!=null,r.getBoolean("disponivel"),r.getBoolean("cancelado"),
            r.getString("horarios_originais"),r.getString("funcionamento"),ItineraryDAO.date(r,"data"),ItineraryDAO.date(r,"data_fim"),r.getString("inicio_atual"),r.getString("horario_fim"),r.getString("cidade")));}}
        return items;
    }
}
