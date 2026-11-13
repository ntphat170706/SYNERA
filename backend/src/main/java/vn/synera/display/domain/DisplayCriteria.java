package vn.synera.display.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "display_criteria")
public class DisplayCriteria extends BaseEntity {
  @Column(name = "npp_id")
  public Long nppId;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "rules_json", nullable = false, columnDefinition = "text")
  public String rulesJson;

  @Column(name = "status", nullable = false)
  public String status;
}
