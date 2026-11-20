package br.com.roleout.model;

import java.time.LocalDate;

public class Role {

    private br.com.roleout.model.RoleCharacteristics caracteristicas;
    public br.com.roleout.model.RoleCharacteristics getCaracteristicas() { return caracteristicas; }
    public void setCaracteristicas(br.com.roleout.model.RoleCharacteristics value) { caracteristicas = value; }
    private java.util.List<br.com.roleout.model.OpeningHours.Rule> funcionamento;
    public java.util.List<br.com.roleout.model.OpeningHours.Rule> getFuncionamento() { return funcionamento; }
    public void setFuncionamento(java.util.List<br.com.roleout.model.OpeningHours.Rule> value) { funcionamento = value; }
    private boolean cancelado;
    private String motivoCancelamento;
    public boolean isCancelado() { return cancelado; }
    public void setCancelado(boolean value) { cancelado = value; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String value) { motivoCancelamento = value; }
    private String horarioFim;
    public String getHorarioFim() { return horarioFim; }
    public void setHorarioFim(String value) { horarioFim = value; }
    private String horario;
    public String getHorario() { return horario; }
    public void setHorario(String value) { horario = value; }
    private int id;
    private String nome;
    private String descricao;
    private String local;
    private String cidade;
    private LocalDate data;
    private LocalDate dataFim;
    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate value) { dataFim = value; }
    private String categoria;
    private double valor;
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;

    public java.math.BigDecimal getLatitude() { return latitude; }
    public void setLatitude(java.math.BigDecimal value) { latitude = value; }
    public java.math.BigDecimal getLongitude() { return longitude; }
    public void setLongitude(java.math.BigDecimal value) { longitude = value; }

    public Role() {
    }

    public Role(int id, String nome, String descricao, String local,
                String cidade, LocalDate data, String categoria, double valor) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.local = local;
        this.cidade = cidade;
        this.data = data;
        this.categoria = categoria;
        this.valor = valor;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(double valor) {
        this.valor = valor;
    }
    public String getDataFormatada() {
    if (data == null) {
        return "Data não informada";
    }

    return data.format(
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );
}
}
