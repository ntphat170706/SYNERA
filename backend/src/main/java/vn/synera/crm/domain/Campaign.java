package vn.synera.crm.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "campaign")
public class Campaign extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "group_name", nullable = false)
  public String groupName;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "message", nullable = false, columnDefinition = "text")
  public String message;
}
