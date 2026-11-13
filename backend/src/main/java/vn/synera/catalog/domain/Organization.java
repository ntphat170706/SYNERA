package vn.synera.catalog.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "organization")
public class Organization extends BaseEntity {
  @Column(name = "code", nullable = false)
  public String code;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "parent_id")
  public Long parentId;

  @Column(name = "region", nullable = false)
  public String region;
}
