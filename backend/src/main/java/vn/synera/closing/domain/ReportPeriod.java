package vn.synera.closing.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "report_period")
public class ReportPeriod extends BaseEntity {
  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "start_date", nullable = false)
  public LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  public LocalDate endDate;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "closed_by")
  public Long closedBy;

  @Column(name = "locked_at")
  public Instant lockedAt;
}
