package vn.synera.route.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "visit")
public class Visit extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "business_day", nullable = false)
  public LocalDate day;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "checked_in_at")
  public Instant checkedInAt;

  @Column(name = "checked_out_at")
  public Instant checkedOutAt;

  @Column(name = "latitude")
  public Double latitude;

  @Column(name = "longitude")
  public Double longitude;

  @Column(name = "distance_metres")
  public Double distanceMetres;

  @Column(name = "outside_radius")
  public boolean outsideRadius;

  @Column(name = "reason", columnDefinition = "text")
  public String reason;
}
