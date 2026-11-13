package vn.synera.iam.domain;

import jakarta.persistence.*;
import java.time.Instant;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken extends BaseEntity {
  @Column(name = "user_id", nullable = false)
  public Long userId;

  @Column(name = "token_hash", nullable = false)
  public String tokenHash;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(nullable = false)
  public boolean consumed;
}
