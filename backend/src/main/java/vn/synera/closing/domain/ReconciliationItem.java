package vn.synera.closing.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "reconciliation_item")
public class ReconciliationItem extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "line_id")
  public Long lineId;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  public String description;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;
}
