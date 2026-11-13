package vn.synera.credit.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import vn.synera.common.BaseEntity;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "credit_ledger")
public class LedgerEntry extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "doc_type", nullable = false)
  public String docType;

  @Column(name = "doc_id", nullable = false)
  public Long docId;

  @Column(name = "debit", nullable = false, precision = 18, scale = 2)
  public BigDecimal debit = BigDecimal.ZERO;

  @Column(name = "credit", nullable = false, precision = 18, scale = 2)
  public BigDecimal credit = BigDecimal.ZERO;

  @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
  public BigDecimal balanceAfter = BigDecimal.ZERO;

  @Column(name = "posted_at", nullable = false)
  public Instant postedAt;
}
