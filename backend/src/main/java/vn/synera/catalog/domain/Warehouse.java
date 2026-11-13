package vn.synera.catalog.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "warehouse")
public class Warehouse extends BaseEntity {
  @Column(name = "org_id", nullable = false)
  public Long orgId;

  @Column(name = "code", nullable = false)
  public String code;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "type", nullable = false)
  public String type;
}
