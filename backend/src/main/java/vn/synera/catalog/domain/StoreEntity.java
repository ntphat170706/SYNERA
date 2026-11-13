package vn.synera.catalog.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "store")
public class StoreEntity extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "route_id")
  public Long routeId;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "owner")
  public String owner;

  @Column(name = "phone", nullable = false)
  public String phone;

  @Column(name = "address", nullable = false)
  public String address;

  @Column(name = "latitude")
  public Double latitude;

  @Column(name = "longitude")
  public Double longitude;

  @Column(name = "group_name", nullable = false)
  public String groupName;

  @Column(name = "status", nullable = false)
  public String status;
}
