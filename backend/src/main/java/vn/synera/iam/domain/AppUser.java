package vn.synera.iam.domain;

import jakarta.persistence.*;
import java.time.*;
import vn.synera.common.BaseEntity;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {
  @Column(name = "username", nullable = false)
  public String username;

  @Column(name = "password_hash", nullable = false)
  public String passwordHash;

  @Column(name = "display_name", nullable = false)
  public String displayName;

  @Column(name = "role", nullable = false)
  public String role;

  @Column(name = "npp_id")
  public Long nppId;

  @Column(name = "region")
  public String region;

  @Column(name = "enabled")
  public boolean enabled;

  @Column(name = "token_version")
  public Long tokenVersion = 0L;

  @Column(name = "failed_attempts")
  public int failedAttempts;

  @Column(name = "locked_until")
  public Instant lockedUntil;

  @Column(name = "otp_required")
  public boolean otpRequired;
}
