package vn.synera.inventory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "stock_balance")
public class StockLot extends BaseEntity {
  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(name = "lot_no", nullable = false)
  public String lotNo;

  @Column(name = "expiry_date")
  public LocalDate expiryDate;

  @Column(name = "qty_on_hand", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyOnHand = BigDecimal.ZERO;

  @Column(name = "qty_reserved", nullable = false, precision = 18, scale = 3)
  public BigDecimal qtyReserved = BigDecimal.ZERO;
}
