package vn.synera.demand.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "demand_plan")
public class DemandPlan extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "plan_month", nullable = false)
  public LocalDate month;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "lines_json", nullable = false, columnDefinition = "text")
  public String linesJson;

  @Column(name = "note", columnDefinition = "text")
  public String note;
}
