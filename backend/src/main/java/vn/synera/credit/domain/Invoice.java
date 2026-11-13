package vn.synera.credit.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "invoice")
public class Invoice extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "order_id")
  public Long orderId;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "remaining", nullable = false, precision = 18, scale = 2)
  public BigDecimal remaining = BigDecimal.ZERO;

  @Column(name = "due_date", nullable = false)
  public LocalDate dueDate;

  @Column(name = "status", nullable = false)
  public String status;
}
