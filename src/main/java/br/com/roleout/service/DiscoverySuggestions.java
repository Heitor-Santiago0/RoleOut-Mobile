package br.com.roleout.service;
import br.com.roleout.model.RoleCharacteristics;
import br.com.roleout.model.OpeningHours;

import br.com.roleout.dao.RoleDAO;
import br.com.roleout.dao.SqlAccess;
import br.com.roleout.dao.DiscoveryDAO;
import br.com.roleout.dao.PreferencesDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.text.Normalizer;
import java.util.*;

/** Suggestions share frequency across foreground and travel checks. No coordinates are persisted. */
public class DiscoverySuggestions implements br.com.roleout.model.DiscoveryData {
    private final SqlAccess sql;
    private final RoleDAO roles;
    private final Clock clock;
    private static final ObjectMapper JSON = new ObjectMapper();
    public DiscoverySuggestions() { this(new SqlAccess(), new RoleDAO(), Clock.systemUTC()); }
    public DiscoverySuggestions(SqlAccess sql, RoleDAO roles, Clock clock) { this.sql=sql; this.roles=roles; this.clock=clock; }
    private Settings defaults() { return new Settings(true,true,0,null); }
    private Settings read(Connection c, int account) throws SQLException {
        var found=new DiscoveryDAO(c).settings(account);return found==null?defaults():found;
    }
    public Settings get(int account) {
        try(var c=sql.connection()) { return read(c,account); }
        catch(SQLException e) { throw new RuntimeException("Suggestion settings lookup failed.",e); }
    }
    private void lock(Connection c,int account) throws SQLException {
        if(!new DiscoveryDAO(c).lockAccount(account))throw new IllegalArgumentException("Conta indisponível.");
    }
    public Settings save(int account,Settings value) {
        if(value==null || value.ativo()==null || value.viagens()==null || value.intervaloHoras()==null || !Set.of(0,24,72,168).contains(value.intervaloHoras())) throw new IllegalArgumentException("Configuração inválida.");
        String city=value.ativo()?Inputs.required(value.cidadeBase(),100):null;
        var clean=new Settings(value.ativo(),value.viagens(),value.intervaloHoras(),city);
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                lock(c,account);
                new DiscoveryDAO(c).save(account,clean);
                c.commit(); return clean;
            } catch(SQLException|RuntimeException e) {c.rollback();throw e;}
        } catch(SQLException e) {throw new RuntimeException("Suggestion settings save failed.",e);}
    }
    public Result evaluate(int account,Context context) {
        if(context==null || context.latitude()==null || context.longitude()==null || !Double.isFinite(context.latitude()) || !Double.isFinite(context.longitude()) || Math.abs(context.latitude())>90 || Math.abs(context.longitude())>180) throw new IllegalArgumentException("Posição inválida.");
        String city=Inputs.required(context.cidade(),100);
        ZoneId zone;
        try {zone=ZoneId.of(Inputs.required(context.fuso(),80));} catch(DateTimeException e){throw new IllegalArgumentException("Fuso inválido.");}
        var now=clock.instant();var local=now.atZone(zone);
        try(var c=sql.connection()) {
            c.setAutoCommit(false);
            try {
                // Also serializes settings changes and concurrent checks from multiple devices.
                lock(c,account);
                var settings=read(c,account);
                boolean away=!normalize(city).equals(normalize(settings.cidadeBase()));
                if(!settings.ativo() || settings.cidadeBase()==null || (away&&!settings.viagens())) {c.commit();return new Result(null);}
                var dao=new DiscoveryDAO(c);
                var last=dao.lastNotice(account);
                if(last!=null && now.isBefore(last.plus(Duration.ofHours(settings.intervaloHoras()==0?1:settings.intervaloHoras())))){c.commit();return new Result(null);}
                var sent=dao.sentSince(account,now.minus(Duration.ofHours(24)));
                String content=new PreferencesDAO(c).content(account);
                PreferencesService.Profile preferences=content==null?null:JSON.readValue(content,PreferencesService.Profile.class);
                if(preferences==null || !preferences.status().equals("CONCLUIDO")) {c.commit();return new Result(null);}
                var matches=new ArrayList<Suggestion>();
                for(var point:roles.buscarProximos(context.latitude(),context.longitude(),5)) {
                    if(sent.contains(point.id()) || !normalize(point.cidade()).equals(normalize(city)) || !availableNow(point,local)) continue;
                    var reasons=exactReasons(point.caracteristicas(),preferences);
                    // Automatic interruptions require a specific match, unlike on-demand category discovery.
                    if(!reasons.isEmpty() && (settings.intervaloHoras()!=0 || sent.size()<3 || reasons.size()>=3)) matches.add(new Suggestion(point.id(),point.nome(),point.cidade(),point.distanciaKm(),reasons,away));
                }
                matches.sort(Comparator.<Suggestion>comparingInt(v->v.motivos().size()).reversed().thenComparingDouble(Suggestion::distanciaKm).thenComparingInt(Suggestion::roleId));
                if(matches.isEmpty()){c.commit();return new Result(null);}
                dao.recordDelivery(account,matches.get(0).roleId(),now,now.minus(Duration.ofHours(24)));
                c.commit();return new Result(matches.get(0));
            } catch(SQLException|java.io.IOException|RuntimeException e){c.rollback();throw e;}
        } catch(SQLException|java.io.IOException e){throw new RuntimeException("Suggestion evaluation failed.",e);}
    }
    static String normalize(String text) {return text==null?"":Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").strip().toLowerCase(Locale.ROOT);}
    static List<String> exactReasons(RoleCharacteristics attrs,PreferencesService.Profile profile) {
        if(attrs==null)return List.of();
        var result=new ArrayList<String>();
        match(result,"Música",attrs.musica(),profile.musica());match(result,"Culinária",attrs.comida(),profile.comida());match(result,"Ambiente",attrs.ambientes(),profile.ambientes());return List.copyOf(result);
    }
    private static void match(List<String> result,String label,List<String> offered,List<String> selected) {
        if(offered==null||selected==null)return;
        var values=offered.stream().filter(v->selected.stream().anyMatch(p->normalize(p).equals(normalize(v)))).distinct().toList();
        if(!values.isEmpty())result.add(label+": "+String.join(", ",values));
    }
    static boolean availableNow(RoleDAO.Ponto p,ZonedDateTime now) {
        var day=now.toLocalDate();String date=day.toString();int minute=now.getHour()*60+now.getMinute();
        if(p.data()!=null && (date.compareTo(p.data())<0 || date.compareTo(p.dataFim()==null?p.data():p.dataFim())>0))return false;
        if(p.horario()!=null && date.equals(p.data()) && minute<minutes(p.horario()))return false;
        if(p.horarioFim()!=null && date.equals(p.dataFim()==null?p.data():p.dataFim()) && minute>=minutes(p.horarioFim()))return false;
        var windows=OpeningHours.on(p.funcionamento(),day);
        if(windows!=null)return windows.stream().anyMatch(w->minute>=minutes(w.inicio())&&minute<minutes(w.fim()));
        return p.data()!=null && p.horario()!=null && p.horarioFim()!=null;
    }
    private static int minutes(String value) {return Integer.parseInt(value.substring(0,2))*60+Integer.parseInt(value.substring(3,5));}
}
