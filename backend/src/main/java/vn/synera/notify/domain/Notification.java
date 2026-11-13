package vn.synera.notify.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {
  @Column(name = "user_id", nullable = false)
  public Long userId;

  @Column(name = "npp_id")
  public Long nppId;

  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "payload", nullable = false, columnDefinition = "text")
  public String payload;

  @Column(name = "event_key", nullable = false)
  public String eventKey;

  @Column(name = "read_at")
  public Instant readAt;
}
