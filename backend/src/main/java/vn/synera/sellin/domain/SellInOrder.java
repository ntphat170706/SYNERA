package vn.synera.sellin.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sell_in_order")
public class SellInOrder extends BaseEntity {
  @Column(name = "receipt_reminded_at")
  public java.time.Instant receiptRemindedAt;

  @Column(name = "received_at")
  public java.time.Instant receivedAt;

  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "code", nullable = false)
  public String code;

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

  @Column(name = "hold_reason")
  public String holdReason;

  @Column(name = "expected_delivery")
  public LocalDate expectedDelivery;

  @Column(name = "submitted_at")
  public Instant submittedAt;

  @Column(name = "delivered_at")
  public Instant deliveredAt;

  @Column(name = "idempotency_key", nullable = false)
  public String idempotencyKey;
}
