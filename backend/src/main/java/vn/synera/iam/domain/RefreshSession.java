package vn.synera.iam.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "refresh_token")
public class RefreshSession extends BaseEntity {
  @Column(name = "user_id", nullable = false)
  public Long userId;

  @Column(name = "token_hash", nullable = false)
  public String tokenHash;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "revoked_at")
  public Instant revokedAt;

  @Column(name = "workspace_key", length = 1200)
  public String workspaceKey;
}
