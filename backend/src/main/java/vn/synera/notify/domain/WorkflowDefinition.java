package vn.synera.notify.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "workflow_definition")
public class WorkflowDefinition extends BaseEntity {
  @Column(name = "entity_type", nullable = false)
  public String entityType;

  @Column(name = "step_no")
  public int stepNo;

  @Column(name = "approver_role", nullable = false)
  public String approverRole;

  @Column(name = "min_amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal minAmount = BigDecimal.ZERO;

  @Column(name = "sla_hours")
  public int slaHours;
}
