package vn.synera.credit.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "credit_limit")
public class CreditAccount extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "limit_amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal limitAmount = BigDecimal.ZERO;

  @Column(name = "balance", nullable = false, precision = 18, scale = 2)
  public BigDecimal balance = BigDecimal.ZERO;

  @Column(name = "overdue_amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal overdueAmount = BigDecimal.ZERO;

  @Column(name = "overdue_days_threshold")
  public int overdueDaysThreshold;

  @Column(name = "temporary_limit", precision = 18, scale = 2)
  public BigDecimal temporaryLimit;

  @Column(name = "temporary_until")
  public Instant temporaryUntil;
}
