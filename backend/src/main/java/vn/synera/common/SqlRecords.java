package vn.synera.common;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

/** Shared persistence plumbing; module Facades own validation, scope, lifecycle and audit. */
@Component
public class SqlRecords {
  private final JdbcTemplate jdbc;

  public SqlRecords(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long insert(String table, Map<String, Object> values) {
    identifier(table);
    values.keySet().forEach(SqlRecords::identifier);
    var fields = new ArrayList<>(values.keySet());
    var key = new GeneratedKeyHolder();
    String sql =
        "insert into "
            + table
            + "("
            + String.join(",", fields)
            + ") values("
            + String.join(",", Collections.nCopies(fields.size(), "?"))
            + ")";
    jdbc.update(
        connection -> {
          var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < fields.size(); i++)
            statement.setObject(i + 1, values.get(fields.get(i)));
          return statement;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public Map<String, Object> row(String table, long id, boolean lock) {
    identifier(table);
    var rows =
        jdbc.queryForList(
            "select * from " + table + " where id=?" + (lock ? " for update" : ""), id);
    if (rows.isEmpty()) throw BusinessException.missing();
    return rows.get(0);
  }

  public static Map<String, Object> mutable(Object... pairs) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
    return map;
  }

  public static Map<String, Object> versioned(Object... pairs) {
    var map = mutable(pairs);
    map.put("version", 0);
    map.put("created_at", Timestamp.from(Instant.now()));
    map.put("updated_at", Timestamp.from(Instant.now()));
    return map;
  }

  public static long id(Map<String, Object> row, String name) {
    return ((Number) row.get(name)).longValue();
  }

  private static void identifier(String name) {
    if (!name.matches("[a-z][a-z0-9_]*"))
      throw new IllegalArgumentException("Invalid internal SQL identifier");
  }
}
