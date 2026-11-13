package vn.synera.crm.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "appointment")
public class Appointment extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(name = "remind_at", nullable = false)
  public Instant remindAt;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "reminded")
  public boolean reminded;
}
