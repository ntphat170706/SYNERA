package vn.synera.integration.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "attachment")
public class Attachment extends BaseEntity {
  @Column(name = "npp_id", nullable = false)
  public Long nppId;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "object_key", nullable = false)
  public String objectKey;

  @Column(name = "filename", nullable = false)
  public String filename;

  @Column(name = "content_type", nullable = false)
  public String contentType;

  @Column(name = "size_bytes", nullable = false)
  public Long sizeBytes;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "content_hash")
  public String contentHash;

  @Column(name = "scan_result")
  public String scanResult;
}
