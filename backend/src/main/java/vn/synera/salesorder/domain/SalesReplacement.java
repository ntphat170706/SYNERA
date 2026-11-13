package vn.synera.salesorder.domain;

import jakarta.persistence.*;
import java.time.Instant;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "sales_replacement")
public class SalesReplacement extends BaseEntity {
  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "rep_id", nullable = false)
  public Long repId;

  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "approved_by")
  public Long approvedBy;

  @Column(nullable = false)
  public String status;

  @Column(name = "due_at", nullable = false)
  public Instant dueAt;

  @Column(nullable = false, length = 2000)
  public String reason;

  @Column(name = "receipt_note", length = 2000)
  public String receiptNote;

  @Column(name = "received_at")
  public Instant receivedAt;

  @Column(name = "notified_at")
  public Instant notifiedAt;

  @Column(name = "notified_by")
  public Long notifiedBy;

  @Column(name = "customer_feedback", length = 2000)
  public String customerFeedback;
}
