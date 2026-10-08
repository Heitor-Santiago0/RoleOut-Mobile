package br.com.roleout.dao;

import br.com.roleout.config.ConnectionFactory;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class SqlAccess {
    private final DataSource source;
    public SqlAccess() { source = null; }
    public SqlAccess(DataSource source) { this.source = java.util.Objects.requireNonNull(source); }
    public Connection connection() throws SQLException {
        return source == null ? ConnectionFactory.getConnection() : source.getConnection();
    }
}
