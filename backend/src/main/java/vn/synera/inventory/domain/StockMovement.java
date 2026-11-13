package vn.synera.inventory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "stock_movement")
public class StockMovement extends BaseEntity {
  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(name = "lot_no", nullable = false)
  public String lotNo;

  @Column(name = "movement_type", nullable = false)
  public String movementType;

  @Column(name = "qty", nullable = false, precision = 18, scale = 3)
  public BigDecimal qty = BigDecimal.ZERO;

  @Column(name = "reference_type", nullable = false)
  public String referenceType;

  @Column(name = "reference_id", nullable = false)
  public Long referenceId;
}
