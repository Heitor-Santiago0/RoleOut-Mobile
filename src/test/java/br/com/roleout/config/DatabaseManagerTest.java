package br.com.roleout.config;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    @Test
    void singletonConcurrentQueriesReturnConnectionsAndClosePool() throws Exception {
        DatabaseManager manager = DatabaseManager.getInstance();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            try (Connection first = ConnectionFactory.getConnection();
                 Connection second = ConnectionFactory.getConnection()) {
                assertNotSame(first, second);
                assertNotSame(first.unwrap(org.h2.jdbc.JdbcConnection.class),
                              second.unwrap(org.h2.jdbc.JdbcConnection.class));
                // Both pool slots are occupied: a third caller must time out.
                assertThrows(SQLException.class, manager::getConnection);
            }
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 32; i++) {
                tasks.add(() -> {
                    assertSame(manager, DatabaseManager.getInstance());
                    try (Connection connection = ConnectionFactory.getConnection();
                         var statement = connection.createStatement();
                         var result = statement.executeQuery("SELECT 1")) {
                        assertTrue(result.next());
                        assertEquals(1, result.getInt(1));
                    }
                    return null;
                });
            }
            for (var future : executor.invokeAll(tasks, 10, TimeUnit.SECONDS)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
            new DatabaseLifecycleListener().contextDestroyed(null);
        }
        manager.close(); // Idempotent shutdown.
        assertThrows(SQLException.class, manager::getConnection);
    }

    @Test
    void rejectsInvalidPoolConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseManager.configuration(
                Map.of("DB_POOL_MAX_SIZE", "1", "DB_POOL_MIN_IDLE", "2")));
        assertThrows(IllegalArgumentException.class, () -> DatabaseManager.configuration(
                Map.of("DB_POOL_CONNECTION_TIMEOUT_MS", "100")));
    }
}
