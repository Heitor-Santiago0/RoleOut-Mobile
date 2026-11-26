package br.com.roleout.model;

import java.util.List;

public interface PublicationData {
    record Photo(String base64, String legenda) {}
    record Stop(int roleId, String nome, String local, int minutos) {}
    record Input(String id, String titulo, String relato, Boolean publico, Integer versao, List<Photo> fotos) {}
    record Version(String id, Integer versao) {}
    record Own(String id, String titulo, String relato, String cidade, List<Stop> paradas,
               List<Photo> fotos, boolean publico, int versao) {}
    record Public(String id, SocialProfileData.Public autor, String titulo, String relato,
                  String cidade, List<Stop> paradas, List<Photo> fotos) {}
    record Card(String id, String titulo, String cidade, int totalParadas, int totalFotos) {}
    record Page(List<Card> itens, String proximo) {}
}
