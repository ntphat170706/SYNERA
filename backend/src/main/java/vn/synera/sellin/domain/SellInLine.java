package vn.synera.sellin.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sell_in_order_line")
public class SellInLine extends BaseEntity {
  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(name = "uom", nullable = false)
  public String uom;

  @Column(name = "qty_ordered", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyOrdered = BigDecimal.ZERO;

  @Column(name = "qty_confirmed", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyConfirmed = BigDecimal.ZERO;

  @Column(name = "qty_shipped", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyShipped = BigDecimal.ZERO;

  @Column(name = "qty_received", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyReceived = BigDecimal.ZERO;

  @Column(name = "qty_damaged", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyDamaged = BigDecimal.ZERO;

  @Column(name = "qty_returned", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyReturned = BigDecimal.ZERO;

  @Column(name = "price", nullable = false, precision = 18, scale = 2)
  public BigDecimal price = BigDecimal.ZERO;

  @Column(name = "discount", nullable = false, precision = 18, scale = 2)
  public BigDecimal discount = BigDecimal.ZERO;

  @Column(name = "base_factor", nullable = false, precision = 18, scale = 3)
  public BigDecimal baseFactor = BigDecimal.ZERO;
}
