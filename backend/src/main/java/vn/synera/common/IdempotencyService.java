package vn.synera.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.iam.ActorFacade;

@Service
public class IdempotencyService {
  private final JdbcTemplate jdbc;
  private final Jsons json;
  private final ActorFacade actors;

  public IdempotencyService(JdbcTemplate jdbc, Jsons json, ActorFacade actors) {
    this.jdbc = jdbc;
    this.json = json;
    this.actors = actors;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Object execute(String key, String operation, Object request, Supplier<Object> command) {
    BusinessException.require(
        key != null && key.matches("[A-Za-z0-9_:.\\-]{8,100}"),
        "Idempotency-Key phải có 8–100 ký tự an toàn.");
    var actor = actors.current();
    actors.lockCurrent();
    String hash = json.hash(List.of(operation, request, actors.scopeKey()));
    var records =
        jdbc.queryForList(
            "select request_hash,response_json from idempotency_record where user_id=? and"
                + " idem_key=?",
            actor.id(),
            key);
    if (!records.isEmpty()) {
      var row = records.get(0);
      if (!hash.equals(row.get("request_hash")))
        throw BusinessException.conflict(
            "DUPLICATE_REQUEST", "Cùng khóa nhưng nội dung/thao tác khác nhau.");
      return json.read((String) row.get("response_json"));
    }
    Object result = command.get();
    jdbc.update(
        "insert into idempotency_record(user_id,idem_key,request_hash,response_json,created_at)"
            + " values(?,?,?,?,?)",
        actor.id(),
        key,
        hash,
        json.write(result),
        Timestamp.from(Instant.now()));
    return result;
  }
}
