package vn.synera.policy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "price_item")
public class PriceItem extends BaseEntity {
  @Column(name = "npp_id")
  public Long nppId;

  @Column(name = "npp_group_id")
  public Long nppGroupId;

  @Column(name = "store_group_id")
  public Long storeGroupId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(name = "uom", nullable = false)
  public String uom;

  @Column(name = "price", nullable = false, precision = 18, scale = 2)
  public BigDecimal price = BigDecimal.ZERO;

  @Column(name = "valid_from", nullable = false)
  public LocalDate validFrom;

  @Column(name = "valid_to", nullable = false)
  public LocalDate validTo;
}
