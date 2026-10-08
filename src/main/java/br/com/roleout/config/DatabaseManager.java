package br.com.roleout.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Singleton pool per application classloader; connections belong to callers. */
public final class DatabaseManager implements AutoCloseable {
    private static final DatabaseManager INSTANCE = new DatabaseManager();
    private HikariDataSource dataSource;
    private boolean closed;
    private DatabaseManager() { }
    public static DatabaseManager getInstance() { return INSTANCE; }

    private synchronized HikariDataSource pool() throws SQLException {
        if (closed) throw new SQLException("Database pool is closed.");
        if (dataSource == null) {
            dataSource = new HikariDataSource(configuration(System.getenv()));
        }
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        // Do not hold the manager lock while waiting for a connection.
        return pool().getConnection();
    }

    static HikariConfig configuration(Map<String, String> env) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("RoleOutPool");
        config.setJdbcUrl(env.getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/roleout"));
        config.setUsername(env.getOrDefault("DB_USER", "roleout"));
        config.setPassword(env.getOrDefault("DB_PASSWORD", "roleout123"));
        int maximum = Integer.parseInt(env.getOrDefault("DB_POOL_MAX_SIZE", "10"));
        int minimum = Integer.parseInt(env.getOrDefault("DB_POOL_MIN_IDLE", "2"));
        long timeout = Long.parseLong(env.getOrDefault("DB_POOL_CONNECTION_TIMEOUT_MS", "5000"));
        if (maximum < 1 || minimum < 0 || minimum > maximum || timeout < 250) {
            throw new IllegalArgumentException("Invalid DB_POOL configuration.");
        }
        config.setMaximumPoolSize(maximum);
        config.setMinimumIdle(minimum);
        config.setConnectionTimeout(timeout);
        config.setValidationTimeout(Math.min(3000, timeout));
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("connectTimeout", "5000");
        config.addDataSourceProperty("socketTimeout", "10000");
        return config;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (dataSource != null) dataSource.close();
    }
}
