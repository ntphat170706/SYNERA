package vn.synera;

import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Same financial and tenant scenarios as H2, against a fresh real MySQL test database. */
@EnabledIfSystemProperty(named = "synera.mysql.local", matches = "true")
class LocalMySqlIntegrationTest extends ApiIntegrationTest {
  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) throws Exception {
    var settings = new Properties();
    try (var input = Files.newInputStream(Path.of(System.getProperty("synera.mysql.config")))) {
      settings.load(input);
    }
    String url = settings.getProperty("url");
    if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:330[78]/synera_test_[a-f0-9]+\\?.*"))
      throw new IllegalStateException("Only isolated local test databases are allowed");
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> settings.getProperty("username"));
    registry.add("spring.datasource.password", () -> settings.getProperty("password"));
    registry.add("spring.flyway.user", () -> settings.getProperty("migrationUsername"));
    registry.add("spring.flyway.password", () -> settings.getProperty("migrationPassword"));
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add(
        "spring.flyway.locations",
        () -> "classpath:db/mysql-bootstrap,classpath:db/updates,classpath:db/mysql-updates");
  }
}
