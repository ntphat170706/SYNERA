package vn.synera.iam;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.common.*;
import vn.synera.iam.domain.*;
import vn.synera.iam.repo.*;

@Service
public class AuthService {
  private final AppUserRepository users;
  private final RefreshSessionRepository sessions;
  private final OtpChallengeRepository challenges;
  private final PasswordEncoder encoder;
  private final JwtEncoder jwt;
  private final AuditService audit;
  private final RoleFacade roles;
  private final String issuer, otpMode;
  private final int accessMinutes, refreshDays;
  private final vn.synera.integration.OtpDeliveryFacade delivery;
  private final SecureRandom random = new SecureRandom();

  public AuthService(
      AppUserRepository users,
      RefreshSessionRepository sessions,
      OtpChallengeRepository challenges,
      PasswordEncoder encoder,
      JwtEncoder jwt,
      AuditService audit,
      @Value("${synera.jwt.issuer}") String issuer,
      @Value("${synera.integration.otp-mode}") String otpMode,
      @Value("${synera.jwt.access-minutes}") int minutes,
      @Value("${synera.jwt.refresh-days}") int days,
      vn.synera.integration.OtpDeliveryFacade delivery,
      RoleFacade roles) {
    this.users = users;
    this.sessions = sessions;
    this.challenges = challenges;
    this.encoder = encoder;
    this.jwt = jwt;
    this.audit = audit;
    this.issuer = issuer;
    this.otpMode = otpMode;
    this.accessMinutes = minutes;
    this.refreshDays = days;
    this.delivery = delivery;
    this.roles = roles;
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public Object login(String username, String password) {
    var found = users.findByUsername(username.toLowerCase(Locale.ROOT).trim());
    if (found.isEmpty()) {
      encoder.matches(password, "$2a$12$0X9Whf5ZoZE8FxSIrF5F.OZMTXxvhH2pfM3aJY/vG9gNCLtJmZyM.");
      throw denied();
    }
    var user = users.lockById(found.get().id).orElseThrow(AuthService::denied);
    if (user.role.equals("SCHEDULER")) throw denied();
    if (!user.enabled || user.lockedUntil != null && user.lockedUntil.isAfter(Instant.now()))
      throw new BusinessException(
          "ACCOUNT_LOCKED", "Tài khoản tạm khóa hoặc không thể đăng nhập.", 423);
    if (!encoder.matches(password, user.passwordHash)) {
      user.failedAttempts++;
      if (user.failedAttempts >= 5) user.lockedUntil = Instant.now().plusSeconds(900);
      users.saveAndFlush(user);
      throw denied();
    }
    user.failedAttempts = 0;
    user.lockedUntil = null;
    if (user.otpRequired || roles.requiresOtp(user.id)) {
      delivery.assertAvailable();
      var challenge = new OtpChallenge();
      challenge.userId = user.id;
      String code = String.format("%06d", random.nextInt(1_000_000));
      challenge.codeHash = encoder.encode(code);
      challenge.expiresAt = Instant.now().plusSeconds(300);
      challenges.saveAndFlush(challenge);
      delivery.otp(user.username, code);
      var response = new java.util.LinkedHashMap<String, Object>();
      response.put("requiresOtp", true);
      response.put("challengeId", challenge.id);
      response.put("expiresIn", 300);
      if (delivery.local()) {
        response.put("localOtp", code);
        response.put("mode", "LOCAL_ONLY");
      }
      return response;
    }
    return issue(user);
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public Object verify(Long id, String code) {
    var c = challenges.lockById(id).orElseThrow(AuthService::denied);
    if (c.consumed || c.attempts >= 5 || c.expiresAt.isBefore(Instant.now())) throw denied();
    c.attempts++;
    if (!encoder.matches(code, c.codeHash)) {
      challenges.saveAndFlush(c);
      throw denied();
    }
    c.consumed = true;
    var user = users.lockById(c.userId).filter(x -> x.enabled).orElseThrow(AuthService::denied);
    return issue(user);
  }

  @Transactional
  public Object refresh(String token) {
    String hash = hash(token);
    var userId = sessions.userIdByHash(hash).orElseThrow(AuthService::denied);
    var user = users.lockById(userId).filter(x -> x.enabled).orElseThrow(AuthService::denied);
    var session = sessions.lockByHash(hash).orElseThrow(AuthService::denied);
    if (session.revokedAt != null || session.expiresAt.isBefore(Instant.now())) throw denied();
    session.revokedAt = Instant.now();
    return issue(user, roles.resolve(user.id, session.workspaceKey));
  }

  @Transactional
  public void logout(Long userId) {
    var u = users.lockById(userId).orElseThrow(AuthService::denied);
    u.tokenVersion++;
    sessions
        .findByUserId(userId)
        .forEach(
            s -> {
              if (s.revokedAt == null) s.revokedAt = Instant.now();
            });
    audit.add(userId, "User", userId, "LOGOUT", Map.of());
  }

  @Transactional
  public void logoutRefresh(String token) {
    if (token == null || token.isBlank()) return;
    String fingerprint = hash(token);
    var userId = sessions.userIdByHash(fingerprint);
    if (userId.isEmpty()) return;
    users.lockById(userId.get()).orElseThrow(AuthService::denied);
    var session = sessions.lockByHash(fingerprint).orElseThrow(AuthService::denied);
    if (session.revokedAt == null && session.expiresAt.isAfter(Instant.now())) logout(userId.get());
  }

  @Transactional
  public void password(Long userId, String old, String replacement) {
    BusinessException.require(replacement.length() >= 12, "Mật khẩu phải có ít nhất 12 ký tự.");
    var u = users.lockById(userId).orElseThrow(AuthService::denied);
    if (!encoder.matches(old, u.passwordHash)) throw denied();
    u.passwordHash = encoder.encode(replacement);
    logout(userId);
  }

  private Object issue(AppUser u) {
    return issue(u, roles.resolve(u.id, null));
  }

  @Transactional
  public Object switchWorkspace(Long id, String workspace) {
    var user = users.lockById(id).filter(u -> u.enabled).orElseThrow(AuthService::denied);
    return issue(user, roles.resolve(id, workspace));
  }

  private Object issue(AppUser u, RoleFacade.Workspace grant) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(u.id.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(accessMinutes * 60L))
            .claim("v", u.tokenVersion)
            .claim("role", grant.role())
            .claim("w", grant.key())
            .build();
    String access =
        jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var session = new RefreshSession();
    session.userId = u.id;
    session.workspaceKey = grant.key();
    session.tokenHash = hash(refresh);
    session.expiresAt = now.plusSeconds(refreshDays * 86400L);
    sessions.save(session);
    audit.add(u.id, "User", u.id, "LOGIN", Map.of("role", u.role));
    return Map.of(
        "accessToken",
        access,
        "refreshToken",
        refresh,
        "tokenType",
        "Bearer",
        "expiresIn",
        accessMinutes * 60,
        "user",
        Map.of(
            "id",
            u.id,
            "name",
            u.displayName,
            "role",
            grant.role(),
            "nppId",
            Objects.requireNonNullElse(grant.nppId(), 0L),
            "authorities",
            grant.authorities(),
            "roleCode",
            grant.roleCode(),
            "workspaceKey",
            grant.key()));
  }

  private static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static BusinessException denied() {
    return new BusinessException(
        "INVALID_CREDENTIALS", "Thông tin đăng nhập hoặc mã xác minh không hợp lệ.", 401);
  }
}
