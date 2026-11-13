package vn.synera.trademkt.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "trade_request")
public class TradeRequest extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "budget_id", nullable = false)
  public Long budgetId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "requested", nullable = false, precision = 18, scale = 2)
  public BigDecimal requested = BigDecimal.ZERO;

  @Column(name = "approved", nullable = false, precision = 18, scale = 2)
  public BigDecimal approved = BigDecimal.ZERO;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "reason", columnDefinition = "text")
  public String reason;
}
