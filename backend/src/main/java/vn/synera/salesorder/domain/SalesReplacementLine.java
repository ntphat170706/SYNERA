package vn.synera.salesorder.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sales_replacement_line")
public class SalesReplacementLine extends BaseEntity {
  @Column(name = "replacement_id", nullable = false)
  public Long replacementId;

  @Column(name = "order_line_id", nullable = false)
  public Long orderLineId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(nullable = false)
  public String uom;

  @Column(name = "base_factor", nullable = false, precision = 18, scale = 3)
  public BigDecimal baseFactor;

  @Column(nullable = false, precision = 18, scale = 3)
  public BigDecimal qty;

  @Column(name = "qty_received", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyReceived = BigDecimal.ZERO;
}
