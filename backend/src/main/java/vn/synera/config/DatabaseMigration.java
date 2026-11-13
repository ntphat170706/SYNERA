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
    try (var connection =
        java.sql.DriverManager.getConnection(
            url, value("MIGRATION_USERNAME"), value("MYSQL_ROOT_PASSWORD"))) {
      restrictRuntimeGrants(
          connection,
          value("DB_NAME"),
          System.getenv().getOrDefault("DB_USERNAME", "synera"),
          System.getenv().getOrDefault("DB_APP_HOST", "%"));
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("Could not restrict runtime database grants", e);
    }
  }

  static void restrictRuntimeGrants(
      java.sql.Connection connection, String database, String account, String host)
      throws java.sql.SQLException {
    if (!database.matches("[A-Za-z0-9_]+")
        || !account.matches("[A-Za-z0-9_]+")
        || !Set.of("%", "localhost").contains(host))
      throw new IllegalStateException("Invalid database grant target");
    try (var statement = connection.createStatement()) {
      boolean partialRevokes;
      try (var settings = statement.executeQuery("SELECT @@GLOBAL.partial_revokes")) {
        settings.next();
        partialRevokes = settings.getBoolean(1);
      }
      String exactDatabase = partialRevokes ? database : database.replace("_", "\\_");
      // Docker escapes underscores in database grants; the portable bootstrap historically did not.
      // Revoke both spellings without failing when one has never been granted.
      for (String target : new LinkedHashSet<>(List.of(database, exactDatabase))) {
        statement.execute(
            "REVOKE IF EXISTS ALL PRIVILEGES ON `"
                + target
                + "`.* FROM '"
                + account
                + "'@'"
                + host
                + "'");
      }
      statement.execute(
          "GRANT SELECT,INSERT,UPDATE,DELETE ON `"
              + exactDatabase
              + "`.* TO '"
              + account
              + "'@'"
              + host
              + "'");
    }
  }
}
