package vn.synera.iam.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "otp_challenge")
public class OtpChallenge extends BaseEntity {
  @Column(name = "user_id", nullable = false)
  public Long userId;

  @Column(name = "code_hash", nullable = false)
  public String codeHash;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "attempts")
  public int attempts;

  @Column(name = "consumed")
  public boolean consumed;
}
