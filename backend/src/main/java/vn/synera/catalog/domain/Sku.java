package vn.synera.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sku")
public class Sku extends BaseEntity {
  @Column(name = "code", nullable = false)
  public String code;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "category", nullable = false)
  public String category;

  @Column(name = "active")
  public boolean active;

  @Column(name = "min_order", nullable = false, precision = 18, scale = 3)
  public BigDecimal minOrder = BigDecimal.ZERO;

  @Column(name = "base_unit", nullable = false)
  public String baseUnit;
}
