package vn.synera.delivery.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "return_line")
public class ReturnLine extends BaseEntity {
  @Column(name = "return_id", nullable = false)
  public Long returnId;

  @Column(name = "order_line_id", nullable = false)
  public Long orderLineId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(precision = 18, scale = 3, nullable = false)
  public BigDecimal qty;

  @Column(name = "base_factor", precision = 18, scale = 3, nullable = false)
  public BigDecimal baseFactor;

  @Column(precision = 18, scale = 2, nullable = false)
  public BigDecimal amount;
}
