package vn.synera.inventory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "stock_allocation")
public class Allocation extends BaseEntity {
  @Column(name = "document_type", nullable = false)
  public String documentType;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "line_id", nullable = false)
  public Long lineId;

  @Column(name = "lot_id", nullable = false)
  public Long lotId;

  @Column(name = "qty", nullable = false, precision = 18, scale = 3)
  public BigDecimal qty = BigDecimal.ZERO;

  @Column(name = "shipped")
  public boolean shipped;
}
