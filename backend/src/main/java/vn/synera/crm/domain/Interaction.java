package vn.synera.crm.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "interaction")
public class Interaction extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "note", nullable = false, columnDefinition = "text")
  public String note;

  @Column(name = "occurred_at", nullable = false)
  public Instant occurredAt;
}
