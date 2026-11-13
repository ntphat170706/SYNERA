package vn.synera.performance.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "target")
public class Target extends BaseEntity {
  @Column(name = "created_by")
  public Long createdBy;

  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "period_id", nullable = false)
  public Long periodId;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "reward_pct", nullable = false, precision = 18, scale = 2)
  public BigDecimal rewardPct = BigDecimal.ZERO;
}
