package vn.synera.config;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationMySqlTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.4.13")
          .withDatabaseName("synera_grant")
          .withUsername("synera")
          .withEnv("MYSQL_ROOT_HOST", "%")
          .withPassword("test-only-password");

  @Test
  void dockerEscapedGrantBecomesCrudOnlyAndCanBeAppliedAgain() throws Exception {
    verifyRestrictedAccount("synera", false);
  }

  @Test
  void portableUnescapedGrantIsRemovedWithoutKeepingWildcardAccess() throws Exception {
    verifyRestrictedAccount("portable", false);
  }

  @Test
  void accountWithNoSchemaGrantCanBeRestricted() throws Exception {
    verifyRestrictedAccount("fresh", true);
  }

  @Test
  void literalSchemaNamesWorkWithPartialRevokesEnabled() throws Exception {
    try (var root = admin();
        var sql = root.createStatement()) {
      sql.execute("SET GLOBAL partial_revokes = ON");
      try {
        verifyRestrictedAccount("partial", false);
      } finally {
        sql.execute("SET GLOBAL partial_revokes = OFF");
      }
    }
  }

  private Connection admin() throws SQLException {
    return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
  }

  private void verifyRestrictedAccount(String account, boolean noGrant) throws Exception {
    try (var root = admin();
        var sql = root.createStatement()) {
      sql.execute("CREATE TABLE IF NOT EXISTS grant_probe (id INT PRIMARY KEY, amount INT)");
      sql.execute("CREATE DATABASE IF NOT EXISTS syneraXgrant");
      sql.execute("CREATE TABLE IF NOT EXISTS syneraXgrant.grant_probe (id INT)");
      if (!account.equals("synera")) {
        sql.execute("CREATE USER '" + account + "'@'%' IDENTIFIED BY 'test-only-password'");
        if (!noGrant) sql.execute("GRANT ALL ON `synera_grant`.* TO '" + account + "'@'%'");
      }
      DatabaseMigration.restrictRuntimeGrants(root, "synera_grant", account, "%");
      DatabaseMigration.restrictRuntimeGrants(root, "synera_grant", account, "%");
    }
    try (var app = DriverManager.getConnection(MYSQL.getJdbcUrl(), account, MYSQL.getPassword());
        var sql = app.createStatement()) {
      sql.executeUpdate("INSERT INTO grant_probe (id, amount) VALUES (1, 2)");
      sql.executeUpdate("UPDATE grant_probe SET amount = 3 WHERE id = 1");
      try (var result = sql.executeQuery("SELECT amount FROM grant_probe WHERE id = 1")) {
        assertTrue(result.next());
        assertEquals(3, result.getInt(1));
      }
      sql.executeUpdate("DELETE FROM grant_probe WHERE id = 1");
      assertThrows(SQLException.class, () -> sql.execute("CREATE TABLE forbidden_ddl (id INT)"));
      assertThrows(
          SQLException.class, () -> sql.executeQuery("SELECT * FROM syneraXgrant.grant_probe"));
    }
  }
}
