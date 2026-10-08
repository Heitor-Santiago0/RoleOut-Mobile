package br.com.roleout.service;

import br.com.roleout.dao.RoleDAO;
import br.com.roleout.model.Role;

import java.util.List;

public class RoleService {

    private final RoleDAO roleDAO;

    public RoleService() { this(new RoleDAO()); }

    public RoleService(RoleDAO roleDAO) {
        this.roleDAO = java.util.Objects.requireNonNull(roleDAO);
    }

    public List<Role> consultarPagina(String categoria, String cidade, Double valorMaximo,
                                     int limite, int deslocamento) {
        if (limite < 1 || limite > 100 || deslocamento < 0 || deslocamento > 100000) {
            throw new IllegalArgumentException("Use limite entre 1 e 100 e deslocamento entre 0 e 100000.");
        }
        if (valorMaximo != null && (!Double.isFinite(valorMaximo) || valorMaximo < 0)) {
            throw new IllegalArgumentException("O orçamento deve ser um número finito e não negativo.");
        }
        return roleDAO.consultarPagina(filtro(categoria, 100), filtro(cidade, 150),
                valorMaximo, limite, deslocamento);
    }

    private String filtro(String valor, int tamanhoMaximo) {
        if (valor == null) return null;
        valor = valor.trim();
        if (valor.isEmpty() || valor.length() > tamanhoMaximo) {
            throw new IllegalArgumentException("Filtro vazio ou muito longo.");
        }
        return valor;
    }

    public void cadastrar(Role role) {
        validar(role);
        roleDAO.inserir(role);
    }

    public List<Role> listarTodos() {
        return roleDAO.listarTodos();
    }

    public void atualizar(Role role) {
        if (role.getId() <= 0) {
            throw new IllegalArgumentException("ID do rolê inválido.");
        }

        validar(role);
        roleDAO.atualizar(role);
    }

    public void excluir(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID do rolê inválido.");
        }

        roleDAO.excluir(id);
    }

    public Role buscarPorId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID do rolê inválido.");
        }

        return roleDAO.buscarPorId(id);
    }

    public Role buscarPublicadoPorId(int id) {
        if (id <= 0) throw new IllegalArgumentException("ID do rolê inválido.");
        return roleDAO.buscarPublicadoPorId(id);
    }

    public List<Role> buscarSugestoes(
            String categoria,
            String cidade,
            double valorMaximo) {

        if (categoria == null || categoria.isBlank()) {
            throw new IllegalArgumentException("A categoria é obrigatória.");
        }

        if (cidade == null || cidade.isBlank()) {
            throw new IllegalArgumentException("A cidade é obrigatória.");
        }

        if (!Double.isFinite(valorMaximo) || valorMaximo < 0) {
            throw new IllegalArgumentException("O orçamento não pode ser negativo.");
        }

        return roleDAO.buscarSugestoes(
            categoria,
            cidade,
            valorMaximo
        );
    }

    private void validar(Role role) {

        if (role == null) {
            throw new IllegalArgumentException("Rolê não pode ser nulo.");
        }

        if (role.getNome() == null || role.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome do rolê é obrigatório.");
        }

        if (role.getCidade() == null || role.getCidade().isBlank()) {
            throw new IllegalArgumentException("A cidade é obrigatória.");
        }

        if (!Double.isFinite(role.getValor()) || role.getValor() < 0) {
            throw new IllegalArgumentException("O valor não pode ser negativo.");
        }
    }
}
