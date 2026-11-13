package vn.synera.salesorder.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sales_order")
public class SalesOrder extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "code", nullable = false)
  public String code;

  @Column(name = "client_id", nullable = false)
  public String clientId;

  @Column(name = "idempotency_key", nullable = false)
  public String idempotencyKey;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
  public BigDecimal subtotal = BigDecimal.ZERO;

  @Column(name = "discount", nullable = false, precision = 18, scale = 2)
  public BigDecimal discount = BigDecimal.ZERO;

  @Column(name = "total", nullable = false, precision = 18, scale = 2)
  public BigDecimal total = BigDecimal.ZERO;

  @Column(name = "client_created_at", nullable = false)
  public Instant clientCreatedAt;

  @Column(name = "delivered_at")
  public Instant deliveredAt;
}
