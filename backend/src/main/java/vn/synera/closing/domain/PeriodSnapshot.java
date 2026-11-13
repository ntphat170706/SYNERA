package vn.synera.closing.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "period_snapshot")
public class PeriodSnapshot extends BaseEntity {
  @Column(name = "period_id", nullable = false)
  public Long periodId;

  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "debt", nullable = false, precision = 18, scale = 2)
  public BigDecimal debt = BigDecimal.ZERO;

  @Column(name = "captured_at", nullable = false)
  public Instant capturedAt;
}
