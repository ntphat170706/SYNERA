package vn.synera.closing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "adjustment")
public class Adjustment extends BaseEntity {
  @Column(name = "original_period_id", nullable = false)
  public Long originalPeriodId;

  @Column(name = "posted_period_id")
  public Long postedPeriodId;

  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "delta", nullable = false, precision = 18, scale = 2)
  public BigDecimal delta = BigDecimal.ZERO;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  public String reason;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "approved_by")
  public Long approvedBy;
}
