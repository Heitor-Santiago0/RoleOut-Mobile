package br.com.roleout.model;

import java.util.List;

/** HTTP-neutral data shared by the itinerary service and persistence layer. */
public interface ItineraryData {
    public record StopInput(Integer roleId, Integer minutos, String horario) { public StopInput(Integer roleId, Integer minutos) { this(roleId, minutos, null); } }
    public record Input(String nome, String cidade, String data, String partida, Integer duracaoMinutos,
                        Integer pessoas, Long orcamentoCentavos, String modoOrcamento, List<StopInput> paradas) {}
    public record Stop(int roleId, String nome, String local, int minutos, long precoCentavos, boolean concluida, String horario, boolean cancelado) {
        public Stop(int roleId, String nome, String local, int minutos, long precoCentavos, boolean concluida, String horario) {
            this(roleId, nome, local, minutos, precoCentavos, concluida, horario, false);
        }
    }
    public record Progress(Boolean concluida) {}
    public record Rename(String nome) {}
    public record Summary(int id, String nome, String cidade, String data, String partida, int pessoas,
                          int duracaoMinutos, long orcamentoCentavos, String modoOrcamento,
                          long totalCentavos, String criadoEm, int totalParadas, int paradasConcluidas) {}
    public record Detail(Summary roteiro, List<Stop> paradas) {}
}
