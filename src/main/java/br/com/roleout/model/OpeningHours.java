package br.com.roleout.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;

/** Local civil times of the activity, not a booking or live availability guarantee. */
public final class OpeningHours {
    public record Window(String inicio, String fim) {}
    public record Rule(String dia, List<Window> intervalos) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private OpeningHours() {}
    public static List<Rule> validate(List<Rule> rules) {
        if (rules == null) return null;
        if (rules.size() > 67) throw new IllegalArgumentException("Use até 7 dias semanais e 60 exceções.");
        var keys = new HashSet<String>(); var result = new ArrayList<Rule>(); int exceptions = 0;
        for (Rule rule : rules) {
            if (rule == null || rule.dia() == null || !keys.add(rule.dia()) || rule.intervalos() == null || rule.intervalos().size() > 4) invalid();
            if (!rule.dia().matches("[1-7]")) {
                try {
                    if (!rule.dia().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(rule.dia()).getYear() < 2000 || LocalDate.parse(rule.dia()).getYear() > 2100 || ++exceptions > 60) invalid();
                } catch (java.time.DateTimeException e) { invalid(); }
            }
            var windows = new ArrayList<>(rule.intervalos());
            for (Window w : windows) {
                if (w == null || !clock(w.inicio(), false) || !clock(w.fim(), true) || minute(w.inicio()) >= minute(w.fim())) invalid();
            }
            windows.sort(Comparator.comparing(Window::inicio));
            int last = -1;
            for (Window w : windows) { if (minute(w.inicio()) < last) invalid(); last = minute(w.fim()); }
            result.add(new Rule(rule.dia(), List.copyOf(windows)));
        }
        result.sort(Comparator.comparing(Rule::dia));
        return List.copyOf(result);
    }
    private static boolean clock(String s, boolean end) { return s != null && (s.matches("([01][0-9]|2[0-3]):[0-5][0-9]") || end && s.equals("24:00")); }
    private static int minute(String s) { return Integer.parseInt(s.substring(0,2))*60 + Integer.parseInt(s.substring(3)); }
    private static void invalid() { throw new IllegalArgumentException("Confira os horários: datas únicas, até 4 intervalos por dia, sem sobreposição. Use 24:00 somente no fechamento."); }
    public static String encode(List<Rule> rules) {
        try { return rules == null ? null : JSON.writeValueAsString(rules); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot encode hours", e); }
    }
    public static List<Rule> decode(String value) {
        try { return value == null ? null : validate(Arrays.asList(JSON.readValue(value, Rule[].class))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid stored hours", e); }
    }
    public static List<Window> on(List<Rule> rules, LocalDate day) {
        if (rules == null) return null;
        Rule weekly = null;
        for (Rule rule : rules) {
            if (rule.dia().equals(day.toString())) return rule.intervalos();
            if (rule.dia().equals(Integer.toString(day.getDayOfWeek().getValue()))) weekly = rule;
        }
        return weekly == null ? null : weekly.intervalos();
    }
    public static boolean fits(List<Rule> rules, LocalDate day, int start, int end) {
        for (int cursor = start; cursor < end;) {
            int offset = cursor / 1440, from = cursor % 1440, to = Math.min(end - offset * 1440, 1440);
            var windows = on(rules, day.plusDays(offset));
            if (windows != null) {
                int covered = from;
                for (Window w : windows) { if (minute(w.inicio()) <= covered && minute(w.fim()) > covered) covered = minute(w.fim()); }
                if (covered < to) return false;
            }
            cursor = (offset * 1440) + to;
        }
        return true;
    }
    public static String describe(List<Rule> rules) {
        if (rules == null || rules.isEmpty()) return "Não informado";
        String[] days = {"", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"};
        return rules.stream().map(r -> (r.dia().length()==1 ? days[Integer.parseInt(r.dia())] : r.dia()) + ": " + (r.intervalos().isEmpty() ? "Fechado" : r.intervalos().stream().map(w -> w.inicio()+"–"+w.fim()).collect(java.util.stream.Collectors.joining(", ")))).collect(java.util.stream.Collectors.joining("\n"));
    }
}
