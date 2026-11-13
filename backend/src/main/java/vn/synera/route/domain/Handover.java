package vn.synera.route.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "handover")
public class Handover extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "from_rep_id", nullable = false)
  public Long fromRepId;

  @Column(name = "to_rep_id", nullable = false)
  public Long toRepId;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  public String reason;
}
