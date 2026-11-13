package vn.synera.inventory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "stocktake_line")
public class StocktakeLine extends BaseEntity {
  @Column(name = "stocktake_id", nullable = false)
  public Long stocktakeId;

  @Column(name = "lot_id", nullable = false)
  public Long lotId;

  @Column(name = "baseline_version", nullable = false)
  public Long baselineVersion;

  @Column(name = "system_qty", precision = 18, scale = 3, nullable = false)
  public BigDecimal systemQty;

  @Column(name = "counted_qty", precision = 18, scale = 3, nullable = false)
  public BigDecimal countedQty;
}
