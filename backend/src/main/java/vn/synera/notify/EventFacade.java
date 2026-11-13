package vn.synera.notify;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.common.Jsons;

@Service
public class EventFacade {
  private final JdbcTemplate jdbc;
  private final Jsons json;

  public EventFacade(JdbcTemplate jdbc, Jsons json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  public void add(String type, Long id, Long nppId, Long actor, Object payload) {
    var now = Timestamp.from(Instant.now());
    jdbc.update(
        "insert into"
            + " domain_event_outbox(event_id,event_type,aggregate_id,npp_id,user_id,payload,status,attempts,available_at,created_at)"
            + " values(?,?,?,?,?,?,'PENDING',0,?,?)",
        UUID.randomUUID().toString(),
        type,
        id,
        nppId,
        actor,
        json.write(payload),
        now,
        now);
  }
}
