package vn.synera.trademkt.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "budget_allocation")
public class Budget extends BaseEntity {
  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "committed", nullable = false, precision = 18, scale = 2)
  public BigDecimal committed = BigDecimal.ZERO;

  @Column(name = "period_start", nullable = false)
  public LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  public LocalDate periodEnd;
}
