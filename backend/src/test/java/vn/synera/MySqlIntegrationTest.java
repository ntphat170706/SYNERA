package vn.synera;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class MySqlIntegrationTest extends ApiIntegrationTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.4.13")
          .withDatabaseName("synera_test")
          .withUsername("synera")
          .withEnv("MYSQL_ROOT_HOST", "%")
          .withPassword("test-only-password");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry r) {
    // PER_CLASS Spring tests prepare their context before JUnit's BeforeAll callbacks.
    // Start here so datasource suppliers never read an unmapped container port.
    MYSQL.start();
    r.add(
        "spring.flyway.locations",
        () -> "classpath:db/mysql-bootstrap,classpath:db/updates,classpath:db/mysql-updates");
    r.add("spring.datasource.url", MYSQL::getJdbcUrl);
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
    r.add("spring.flyway.user", () -> "root");
    r.add("spring.flyway.password", MYSQL::getPassword);
    r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }
}
