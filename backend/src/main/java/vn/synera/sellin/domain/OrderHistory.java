package vn.synera.sellin.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "order_status_history")
public class OrderHistory extends BaseEntity {
  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "note", columnDefinition = "text")
  public String note;

  @Column(name = "occurred_at", nullable = false)
  public Instant occurredAt;
}
