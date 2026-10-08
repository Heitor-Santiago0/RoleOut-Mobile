package br.com.roleout.config;
import java.sql.Connection;
import java.sql.SQLException;
/** Compatibility facade for the existing web DAOs. */
public final class ConnectionFactory {
    private ConnectionFactory() { }
    public static Connection getConnection() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }
}