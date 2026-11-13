package vn.synera.policy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "promotion")
public class Promotion extends BaseEntity {
  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "npp_id")
  public Long nppId;

  @Column(name = "npp_group_id")
  public Long nppGroupId;

  @Column(name = "store_group_id")
  public Long storeGroupId;

  @Column(name = "sku_id")
  public Long skuId;

  @Column(name = "min_qty", nullable = false, precision = 18, scale = 3)
  public BigDecimal minQty = BigDecimal.ZERO;

  @Column(name = "min_amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal minAmount = BigDecimal.ZERO;

  @Column(name = "percent_off", nullable = false, precision = 18, scale = 2)
  public BigDecimal percentOff = BigDecimal.ZERO;

  @Column(name = "priority")
  public int priority;

  @Column(name = "exclusion_group")
  public String exclusionGroup;

  @Column(name = "budget", nullable = false, precision = 18, scale = 2)
  public BigDecimal budget = BigDecimal.ZERO;

  @Column(name = "used_budget", nullable = false, precision = 18, scale = 2)
  public BigDecimal usedBudget = BigDecimal.ZERO;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "start_date", nullable = false)
  public LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  public LocalDate endDate;

  @Column(name = "required_skus_json", columnDefinition = "text")
  public String requiredSkusJson;

  @Column(name = "max_uses")
  public Long maxUses;

  @Column(name = "used_uses", nullable = false)
  public long usedUses;
}
