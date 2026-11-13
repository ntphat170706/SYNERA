package vn.synera.notify.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "task")
public class TaskEntity extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id")
  public Long storeId;

  @Column(name = "assignee_id", nullable = false)
  public Long assigneeId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "reference_type", nullable = false)
  public String referenceType;

  @Column(name = "reference_id", nullable = false)
  public Long referenceId;
}
