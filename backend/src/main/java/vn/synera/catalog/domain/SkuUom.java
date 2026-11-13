package vn.synera.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sku_uom")
public class SkuUom extends BaseEntity {
  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(name = "code", nullable = false)
  public String code;

  @Column(name = "factor_to_base", nullable = false, precision = 18, scale = 3)
  public BigDecimal factorToBase = BigDecimal.ZERO;

  @Column(name = "multiple", nullable = false, precision = 18, scale = 3)
  public BigDecimal multiple = BigDecimal.ZERO;
}
