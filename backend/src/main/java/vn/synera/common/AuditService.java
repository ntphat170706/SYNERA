package vn.synera.common;

import java.sql.Timestamp;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;
  private final Jsons json;

  public AuditService(JdbcTemplate jdbc, Jsons json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  public void add(Long actor, String entity, Long id, String action, Object data) {
    change(actor, entity, id, action, null, data, data);
  }

  @Transactional
  public void change(
      Long actor, String entity, Long id, String action, Object before, Object after, Object data) {
    var attributes =
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
    String ip =
        attributes
                instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
            ? servlet.getRequest().getRemoteAddr()
            : null;
    jdbc.update(
        "insert into"
            + " audit_log(user_id,entity_type,entity_id,action,payload,request_id,created_at,client_ip,before_json,after_json)"
            + " values(?,?,?,?,?,?,?,?,?,?)",
        actor,
        entity,
        id,
        action,
        json.write(redact(data)),
        MDC.get("requestId"),
        Timestamp.from(Instant.now()),
        ip,
        before == null ? null : json.write(redact(before)),
        after == null ? null : json.write(redact(after)));
  }

  private Object redact(Object input) {
    if (input == null) return null;
    Object value = json.read(json.write(input));
    return redactValue(value);
  }

  /** Internal callers must assert entity scope before returning this projection. */
  public java.util.List<java.util.Map<String, Object>> history(String entity, Long id) {
    return jdbc.query(
        "select user_id,action,payload,created_at from audit_log where entity_type=? and"
            + " entity_id=? order by id",
        (r, n) ->
            java.util.Map.<String, Object>of(
                "actorId",
                r.getLong("user_id"),
                "action",
                r.getString("action"),
                "data",
                json.read(r.getString("payload")),
                "at",
                r.getTimestamp("created_at").toInstant()),
        entity,
        id);
  }

  private Object redactValue(Object value) {
    if (value instanceof java.util.Map<?, ?> map) {
      var result = new java.util.LinkedHashMap<String, Object>();
      map.forEach(
          (key, item) ->
              result.put(
                  key.toString(),
                  key.toString()
                          .toLowerCase(java.util.Locale.ROOT)
                          .matches(".*(password|secret|token|otp).*")
                      ? "[REDACTED]"
                      : redactValue(item)));
      return result;
    }
    if (value instanceof java.util.List<?> list)
      return list.stream().map(this::redactValue).toList();
    return value;
  }
}
