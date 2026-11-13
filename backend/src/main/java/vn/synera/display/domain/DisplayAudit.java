package vn.synera.display.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "display_audit")
public class DisplayAudit extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "store_id", nullable = false)
  public Long storeId;

  @Column(name = "criteria_id", nullable = false)
  public Long criteriaId;

  @Column(name = "attachment_id", nullable = false)
  public Long attachmentId;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "ai_score")
  public Double aiScore;

  @Column(name = "confidence")
  public Double confidence;

  @Column(name = "result_json", columnDefinition = "text")
  public String resultJson;

  @Column(name = "final_score")
  public Double finalScore;

  @Column(name = "reviewed_by")
  public Long reviewedBy;

  @Column(name = "provider", nullable = false)
  public String provider;

  @Column(name = "model_version")
  public String modelVersion;
}
