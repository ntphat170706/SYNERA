package vn.synera.policy.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "applied_benefit")
public class AppliedBenefit extends BaseEntity {
  @Column(name = "document_type", nullable = false)
  public String documentType;

  @Column(name = "document_id", nullable = false)
  public Long documentId;

  @Column(name = "promotion_id", nullable = false)
  public Long promotionId;

  @Column(precision = 18, scale = 2, nullable = false)
  public BigDecimal amount;

  @Column(nullable = false)
  public boolean released;
}
