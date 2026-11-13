package vn.synera.route.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "route_stop")
public class RouteStop extends BaseEntity {
  @Column(name = "route_id", nullable = false)
  public Long routeId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "sequence_no")
  public int sequenceNo;

  @Column(name = "weekday")
  public int weekday;
}
