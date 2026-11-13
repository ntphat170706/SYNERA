package vn.synera;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Optional real-MySQL acceptance suite on a dedicated schema; never use the application schema. */
@EnabledIfEnvironmentVariable(named = "SYNERA_MYSQL_TEST_URL", matches = ".+")
class SalesReplacementMySqlTest extends SalesReplacementIntegrationTest {
  @DynamicPropertySource
  static void mysql(DynamicPropertyRegistry r) {
    String url = System.getenv("SYNERA_MYSQL_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:\\d+/synera_npp_verify_[a-zA-Z0-9_]+(?:\\?.*)?"))
      throw new IllegalArgumentException("Use an isolated local synera_npp_verify_* schema.");
    r.add("spring.datasource.url", () -> url);
    r.add("spring.datasource.username", () -> System.getenv("SYNERA_MYSQL_TEST_USER"));
    r.add("spring.datasource.password", () -> System.getenv("SYNERA_MYSQL_TEST_PASSWORD"));
    r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    r.add(
        "spring.flyway.locations",
        () -> "classpath:db/mysql-bootstrap,classpath:db/updates,classpath:db/mysql-updates");
    r.add("spring.flyway.user", () -> "root");
    r.add("spring.flyway.password", () -> System.getenv("SYNERA_MYSQL_TEST_MIGRATION_PASSWORD"));
  }
}
