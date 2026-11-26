package br.com.roleout.model;
import java.util.List;
public interface CommunityData {
    record TextInput(String texto,Integer nota) {}
    record Entry(long id,SocialProfileData.Public autor,String texto,Integer nota,boolean meu) {}
    record Entries(List<Entry> itens,Long proximo,int total,Double media) {}
    record FeedItem(String id,String titulo,String cidade,int paradas,SocialProfileData.Public autor) {}
    record Feed(List<FeedItem> itens,Long proximo) {}
    record ReportInput(String tipo,String alvo,String motivo) {}
    record Report(long id,String tipo,String alvo,String motivo,String conteudo,String estado) {}
    record Decision(Boolean ocultar,String justificativa) {}
    record Credit(String publicacaoId,String autorId,String autorNome,String titulo) {}
    record CopyInput(ItineraryData.Input planejamento) {}
}
