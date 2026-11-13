package vn.synera.inventory.domain;

import jakarta.persistence.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "stocktake")
public class Stocktake extends BaseEntity {
  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(nullable = false)
  public String status;

  @Column(columnDefinition = "text", nullable = false)
  public String reason;
}
