package vn.synera.delivery.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "return_note")
public class ReturnNote extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  public String reason;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "status", nullable = false)
  public String status;
}
