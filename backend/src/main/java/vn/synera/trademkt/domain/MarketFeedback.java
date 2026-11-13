package vn.synera.trademkt.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "market_feedback")
public class MarketFeedback extends BaseEntity {
  @Column(name = "created_by")
  public Long createdBy;

  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "sku_id", nullable = false)
  public Long skuId;

  @Column(name = "topic", nullable = false)
  public String topic;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  public String description;

  @Column(name = "ticket_id")
  public Long ticketId;
}
