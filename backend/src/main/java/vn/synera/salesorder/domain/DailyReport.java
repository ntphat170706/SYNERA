package vn.synera.salesorder.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "daily_report_scoped")
public class DailyReport extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "business_day", nullable = false)
  public LocalDate day;

  @Column(name = "visits")
  public int visits;

  @Column(name = "orders")
  public int orders;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "note", columnDefinition = "text")
  public String note;

  @Column(name = "status", nullable = false)
  public String status;
}
