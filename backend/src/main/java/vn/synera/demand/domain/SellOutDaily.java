package vn.synera.demand.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sell_out_daily")
public class SellOutDaily extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(name = "business_day", nullable = false)
  public LocalDate day;

  @Column(name = "source", nullable = false)
  public String source;

  @Column(name = "qty", nullable = false, precision = 18, scale = 3)
  public BigDecimal qty = BigDecimal.ZERO;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;
}
