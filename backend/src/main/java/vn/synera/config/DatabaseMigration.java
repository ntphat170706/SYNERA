package vn.synera.config;

import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;

/** One-shot process: schema credentials never enter the running web application. */
public final class DatabaseMigration {
  private DatabaseMigration() {}

  private static String value(String key) {
    String direct = System.getenv(key);
    if (direct != null && !direct.isBlank()) return direct;
    try {
      return Files.readString(Path.of("/run/secrets", key)).strip();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Missing migration configuration: " + key);
    }
  }

  public static void run() {
    String url =
        "jdbc:mysql://"
            + value("DB_HOST")
            + ":"
            + System.getenv().getOrDefault("DB_PORT", "3306")
            + "/"
            + value("DB_NAME")
            + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=REQUIRED&characterEncoding=UTF-8";
    Flyway.configure()
        .dataSource(url, value("MIGRATION_USERNAME"), value("MYSQL_ROOT_PASSWORD"))
        .locations(
            "classpath:db/mysql-bootstrap", "classpath:db/updates", "classpath:db/mysql-updates")
        .cleanDisabled(true)
        .load()
        .migrate();
    String database = value("DB_NAME"),
        account = System.getenv().getOrDefault("DB_USERNAME", "synera"),
        host = System.getenv().getOrDefault("DB_APP_HOST", "%");
    if (!database.matches("[A-Za-z0-9_]+")
        || !account.matches("[A-Za-z0-9_]+")
        || !Set.of("%", "localhost").contains(host))
      throw new IllegalStateException("Invalid database grant target");
    try (var connection =
            java.sql.DriverManager.getConnection(
                url, value("MIGRATION_USERNAME"), value("MYSQL_ROOT_PASSWORD"));
        var statement = connection.createStatement()) {
      statement.execute(
          "REVOKE ALL PRIVILEGES ON `" + database + "`.* FROM '" + account + "'@'" + host + "'");
      statement.execute(
          "GRANT SELECT,INSERT,UPDATE,DELETE ON `"
              + database
              + "`.* TO '"
              + account
              + "'@'"
              + host
              + "'");
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("Could not restrict runtime database grants", e);
    }
  }
}
