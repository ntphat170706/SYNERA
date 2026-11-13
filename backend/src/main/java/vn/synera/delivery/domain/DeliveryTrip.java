package vn.synera.delivery.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "delivery_trip")
public class DeliveryTrip extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "driver_id", nullable = false)
  public Long driverId;

  @Column(name = "delivery_date", nullable = false)
  public LocalDate deliveryDate;

  @Column(name = "status", nullable = false)
  public String status;
}
