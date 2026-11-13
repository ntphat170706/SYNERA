package vn.synera.ticket.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "ticket_event")
public class TicketEvent extends BaseEntity {
  @Column(name = "ticket_id", nullable = false)
  public Long ticketId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "note", columnDefinition = "text")
  public String note;

  @Column(name = "occurred_at", nullable = false)
  public Instant occurredAt;
}
