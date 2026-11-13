package vn.synera.ticket.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "ticket")
public class TicketEntity extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id")
  public Long storeId;

  @Column(name = "order_id")
  public Long orderId;

  @Column(name = "order_channel", nullable = false)
  public String orderChannel = "SELL_IN";

  @Column(name = "line_id")
  public Long lineId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "category", nullable = false)
  public String category;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "priority", nullable = false)
  public String priority;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "assigned_to")
  public Long assignedTo;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(name = "escalated")
  public boolean escalated;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  public String description;

  @Column(name = "resolution", columnDefinition = "text")
  public String resolution;
}
