package vn.synera.notify.domain;

import jakarta.persistence.*;
import java.time.Instant;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "approval_request")
public class ApprovalRequest extends BaseEntity {
  @Column(name = "entity_type", nullable = false)
  public String entityType;

  @Column(name = "entity_id", nullable = false)
  public Long entityId;

  @Column(name = "npp_id")
  public Long nppId;

  @Column(name = "step_no", nullable = false)
  public int stepNo;

  @Column(name = "approver_role", nullable = false)
  public String approverRole;

  @Column(nullable = false)
  public String status;

  @Column(name = "approved_by")
  public Long approvedBy;

  @Column(name = "approved_at")
  public Instant approvedAt;

  @Column(columnDefinition = "text")
  public String reason;

  @Column(name = "due_at")
  public Instant dueAt;

  @Column(name = "escalated", nullable = false)
  public boolean escalated;
}
