package vn.synera.crm.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "`lead`")
public class Lead extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "name", nullable = false)
  public String name;

  @Column(name = "phone", nullable = false)
  public String phone;

  @Column(name = "address", nullable = false)
  public String address;

  @Column(name = "latitude")
  public Double latitude;

  @Column(name = "longitude")
  public Double longitude;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "store_id")
  public Long storeId;

  @Column(name = "reason", columnDefinition = "text")
  public String reason;
}
