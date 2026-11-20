package br.com.roleout;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Prevent persistence logic and reverse dependencies from leaking across layers. */
class ArchitectureTest {
    private static final Path ROOT = Path.of("src/main/java/br/com/roleout");

    private void excludes(String folder, String expression) throws Exception {
        var pattern = Pattern.compile(expression);
        try (var files = Files.walk(ROOT.resolve(folder))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertFalse(pattern.matcher(Files.readString(file)).find(),
                        () -> "Layer violation in " + file + ": " + expression);
            }
        }
    }

    @Test void controllersDelegatePersistence() throws Exception {
        excludes("api", "br\\.com\\.roleout\\.(dao|config)\\.");
        excludes("api", "\\b(prepareStatement|createStatement|executeQuery|executeUpdate)\\s*\\(");
    }

    @Test void servicesContainNoJdbcQueries() throws Exception {
        excludes("service", "\\b(prepareStatement|createStatement|executeQuery|executeUpdate)\\s*\\(");
        excludes("service", "\\b(PreparedStatement|ResultSet)\\b");
    }

    @Test void persistenceAndModelsDoNotDependOnControllersOrServices() throws Exception {
        excludes("dao", "br\\.com\\.roleout\\.(api|service)\\.");
        excludes("model", "br\\.com\\.roleout\\.(api|service|dao|config)\\.");
        excludes("dao", "jakarta\\.servlet");
        excludes("model", "jakarta\\.servlet");
    }
}
