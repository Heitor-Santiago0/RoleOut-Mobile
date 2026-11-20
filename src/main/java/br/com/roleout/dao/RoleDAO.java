package br.com.roleout.dao;

import br.com.roleout.config.ConnectionFactory;
import br.com.roleout.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RoleDAO {
    // Legacy test records have no local_id. Linked records require both approvals.
    private static final String PUBLICATION_FILTER = """
            (roles.local_id IS NULL OR EXISTS (
                SELECT 1 FROM locais l JOIN organizacoes o ON o.id = l.organizacao_id
                WHERE l.id = roles.local_id AND l.status = 'PUBLICADO' AND o.status = 'APROVADA'
            ))
            """;
    private final javax.sql.DataSource dataSource;

    public RoleDAO() {
        this.dataSource = null;
    }

    public RoleDAO(javax.sql.DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
    }

    private Connection getConnection() throws SQLException {
        return dataSource == null ? ConnectionFactory.getConnection() : dataSource.getConnection();
    }

    public record Ponto(int id, String nome, String categoria, double latitude,
                        double longitude, double distanciaKm, String cidade, String data, String horario, String dataFim,
                        String horarioFim, List<br.com.roleout.model.OpeningHours.Rule> funcionamento, br.com.roleout.model.RoleCharacteristics caracteristicas) {
        public Ponto(int id, String nome, String categoria, double latitude, double longitude, double distanciaKm) {
            this(id, nome, categoria, latitude, longitude, distanciaKm, null, null, null, null, null, null, null);
        }
    }

    public List<Ponto> buscarProximos(double latitude, double longitude, double raioKm) {
        String sql = """
                SELECT * FROM (
                    SELECT r.id, r.nome, r.categoria, r.cidade, r.data, r.horario, r.data_fim, r.horario_fim, r.funcionamento, r.caracteristicas, l.latitude, l.longitude,
                      6371 * 2 * ASIN(SQRT(LEAST(1,
                        POWER(SIN(RADIANS(l.latitude - ?) / 2), 2) +
                        COS(RADIANS(?)) * COS(RADIANS(l.latitude)) *
                        POWER(SIN(RADIANS(l.longitude - ?) / 2), 2)))) AS distancia_km
                    FROM roles r JOIN locais l ON l.id = r.local_id
                    JOIN organizacoes o ON o.id = l.organizacao_id
                    WHERE r.cancelado=FALSE AND l.status = 'PUBLICADO' AND o.status = 'APROVADA'
                      AND l.latitude BETWEEN -90 AND 90
                      AND l.longitude BETWEEN -180 AND 180
                      AND l.latitude BETWEEN ? AND ?
                ) pontos WHERE distancia_km <= ? ORDER BY distancia_km, id LIMIT 100
                """;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, latitude);
            statement.setDouble(2, latitude);
            statement.setDouble(3, longitude);
            statement.setDouble(4, latitude - raioKm / 111.0);
            statement.setDouble(5, latitude + raioKm / 111.0);
            statement.setDouble(6, raioKm);
            List<Ponto> pontos = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) pontos.add(new Ponto(rs.getInt("id"), rs.getString("nome"),
                        rs.getString("categoria"), rs.getDouble("latitude"),
                        rs.getDouble("longitude"), rs.getDouble("distancia_km"), rs.getString("cidade"), rs.getString("data"), rs.getString("horario"), rs.getString("data_fim"), rs.getString("horario_fim"), br.com.roleout.model.OpeningHours.decode(rs.getString("funcionamento")), br.com.roleout.model.RoleCharacteristics.decode(rs.getString("caracteristicas"))));
            }
            return pontos;
        } catch (SQLException exception) {
            throw new RuntimeException("Erro ao consultar pontos próximos.", exception);
        }
    }

    public List<Role> consultarPagina(String categoria, String cidade, Double valorMaximo,
                                     int limite, int deslocamento) {
        return consultarPagina(categoria, cidade, valorMaximo, limite, deslocamento, null);
    }
    public List<Role> consultarPagina(String categoria, String cidade, Double valorMaximo,
                                     int limite, int deslocamento, java.time.LocalDate data) {
        StringBuilder sql = new StringBuilder("SELECT * FROM roles WHERE cancelado=FALSE AND " + PUBLICATION_FILTER);
        if (categoria != null) sql.append(" AND LOWER(categoria) = LOWER(?)");
        if (cidade != null) sql.append(" AND LOWER(cidade) = LOWER(?)");
        if (valorMaximo != null) sql.append(" AND valor <= ?");
        if (data != null) sql.append(" AND (data IS NULL OR (? BETWEEN data AND COALESCE(data_fim,data)))");
        sql.append(" ORDER BY ").append(valorMaximo == null ? "data, id" : "valor, id");
        sql.append(" LIMIT ? OFFSET ?");
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (categoria != null) statement.setString(index++, categoria);
            if (cidade != null) statement.setString(index++, cidade);
            if (valorMaximo != null) statement.setDouble(index++, valorMaximo);
            if (data != null) statement.setDate(index++, java.sql.Date.valueOf(data));
            statement.setInt(index++, limite);
            statement.setInt(index, deslocamento);
            List<Role> roles = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    roles.add(mapear(rs));
                }
            }
            return roles;
        } catch (SQLException exception) {
            throw new RuntimeException("Erro ao consultar rolês.", exception);
        }
    }

    public Role buscarPublicadoPorId(int id) {
        String sql = "SELECT roles.*, l.latitude, l.longitude FROM roles LEFT JOIN locais l ON l.id=roles.local_id WHERE roles.id = ? AND " + PUBLICATION_FILTER;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                Role role = mapear(rs);
                role.setLatitude(rs.getBigDecimal("latitude"));
                role.setLongitude(rs.getBigDecimal("longitude"));
                return role;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Erro ao consultar rolê publicado.", exception);
        }
    }

    private Role mapear(ResultSet rs) throws SQLException {
        Role role = new Role();
        role.setId(rs.getInt("id"));
        role.setNome(rs.getString("nome"));
        role.setDescricao(rs.getString("descricao"));
        role.setLocal(rs.getString("local"));
        role.setCidade(rs.getString("cidade"));
        var data = rs.getDate("data");
        role.setData(data == null ? null : data.toLocalDate());
        role.setHorario(rs.getString("horario"));
        role.setHorarioFim(rs.getString("horario_fim"));
        role.setCaracteristicas(br.com.roleout.model.RoleCharacteristics.decode(rs.getString("caracteristicas")));
        role.setFuncionamento(br.com.roleout.model.OpeningHours.decode(rs.getString("funcionamento")));
        var endDate = rs.getDate("data_fim");
        role.setDataFim(endDate == null ? null : endDate.toLocalDate());
        role.setCancelado(rs.getBoolean("cancelado"));
        role.setMotivoCancelamento(rs.getString("motivo_cancelamento"));
        role.setCategoria(rs.getString("categoria"));
        role.setValor(rs.getDouble("valor"));
        return role;
    }

    public void inserir(Role role) {

        String sql = """
                INSERT INTO roles
                (nome, descricao, local, cidade, data, categoria, valor)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, role.getNome());
            statement.setString(2, role.getDescricao());
            statement.setString(3, role.getLocal());
            statement.setString(4, role.getCidade());
            if (role.getData() != null) {
    statement.setDate(
        5,
        java.sql.Date.valueOf(role.getData())
    );
} else {
    statement.setNull(5, java.sql.Types.DATE);
}
            statement.setString(6, role.getCategoria());
            statement.setDouble(7, role.getValor());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inserir rolê no banco.", e);
        }
    }
    public List<Role> listarTodos() {

    List<Role> roles = new ArrayList<>();

    String sql = "SELECT * FROM roles WHERE local_id IS NULL ORDER BY data";

    try (
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()
    ) {

        while (resultSet.next()) {

            Role role = new Role();

            role.setId(resultSet.getInt("id"));
            role.setNome(resultSet.getString("nome"));
            role.setDescricao(resultSet.getString("descricao"));
            role.setLocal(resultSet.getString("local"));
            role.setCidade(resultSet.getString("cidade"));

            if (resultSet.getDate("data") != null) {
                role.setData(resultSet.getDate("data").toLocalDate());
            }

            role.setCategoria(resultSet.getString("categoria"));
            role.setValor(resultSet.getDouble("valor"));

            roles.add(role);
        }

    } catch (SQLException e) {
        throw new RuntimeException("Erro ao listar rolês.", e);
    }

    return roles;
}


public void atualizar(Role role) {

    String sql = """
            UPDATE roles
            SET nome = ?,
                descricao = ?,
                local = ?,
                cidade = ?,
                data = ?,
                categoria = ?,
                valor = ?
            WHERE id = ? AND local_id IS NULL
            """;

    try (
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)
    ) {

        statement.setString(1, role.getNome());
        statement.setString(2, role.getDescricao());
        statement.setString(3, role.getLocal());
        statement.setString(4, role.getCidade());
        if (role.getData() != null) {
    statement.setDate(
        5,
        java.sql.Date.valueOf(role.getData())
    );
} else {
    statement.setNull(5, java.sql.Types.DATE);
}
        statement.setString(6, role.getCategoria());
        statement.setDouble(7, role.getValor());
        statement.setInt(8, role.getId());

        statement.executeUpdate();

    } catch (SQLException e) {
        throw new RuntimeException("Erro ao atualizar rolê.", e);
    }
}


public void excluir(int id) {

    String sql = "DELETE FROM roles WHERE id = ? AND local_id IS NULL";

    try (
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)
    ) {

        statement.setInt(1, id);

        statement.executeUpdate();

    } catch (SQLException e) {
        throw new RuntimeException("Erro ao excluir rolê.", e);
    }
}
public Role buscarPorId(int id) {

    String sql = "SELECT * FROM roles WHERE id = ? AND local_id IS NULL";

    try (
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)
    ) {

        statement.setInt(1, id);

        try (ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {

                Role role = new Role();

                role.setId(resultSet.getInt("id"));
                role.setNome(resultSet.getString("nome"));
                role.setDescricao(resultSet.getString("descricao"));
                role.setLocal(resultSet.getString("local"));
                role.setCidade(resultSet.getString("cidade"));

                if (resultSet.getDate("data") != null) {
                    role.setData(resultSet.getDate("data").toLocalDate());
                }

                role.setCategoria(resultSet.getString("categoria"));
                role.setValor(resultSet.getDouble("valor"));

                return role;
            }
        }

    } catch (SQLException e) {
        throw new RuntimeException("Erro ao buscar rolê.", e);
    }

    return null;
}
public List<Role> buscarSugestoes(String categoria, String cidade, double valorMaximo) {

    List<Role> roles = new ArrayList<>();

    String sql = """
            SELECT *
            FROM roles
            WHERE local_id IS NULL AND LOWER(categoria) = LOWER(?)
              AND LOWER(cidade) = LOWER(?)
              AND valor <= ?
            ORDER BY valor ASC
            """;

    try (
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)
    ) {

        statement.setString(1, categoria);
        statement.setString(2, cidade);
        statement.setDouble(3, valorMaximo);

        try (ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {

                Role role = new Role();

                role.setId(resultSet.getInt("id"));
                role.setNome(resultSet.getString("nome"));
                role.setDescricao(resultSet.getString("descricao"));
                role.setLocal(resultSet.getString("local"));
                role.setCidade(resultSet.getString("cidade"));

                if (resultSet.getDate("data") != null) {
                    role.setData(resultSet.getDate("data").toLocalDate());
                }

                role.setCategoria(resultSet.getString("categoria"));
                role.setValor(resultSet.getDouble("valor"));

                roles.add(role);
            }
        }

    } catch (SQLException e) {
        throw new RuntimeException("Erro ao buscar sugestões de rolês.", e);
    }

    return roles;
}
}
