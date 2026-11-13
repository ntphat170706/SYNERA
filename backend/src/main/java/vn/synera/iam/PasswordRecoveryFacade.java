package vn.synera.iam;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.common.*;
import vn.synera.iam.domain.PasswordResetToken;
import vn.synera.iam.repo.*;
import vn.synera.integration.OtpDeliveryFacade;

@Service
public class PasswordRecoveryFacade {
  private final AppUserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final OtpDeliveryFacade delivery;
  private final PasswordEncoder passwords;
  private final AuthService auth;
  private final SecureRandom random = new SecureRandom();

  public PasswordRecoveryFacade(
      AppUserRepository users,
      PasswordResetTokenRepository tokens,
      OtpDeliveryFacade delivery,
      PasswordEncoder passwords,
      AuthService auth) {
    this.users = users;
    this.tokens = tokens;
    this.delivery = delivery;
    this.passwords = passwords;
    this.auth = auth;
  }

  @Transactional
  public Object request(String username) {
    delivery.assertAvailable();
    var found = users.findByUsername(username.toLowerCase(Locale.ROOT).trim());
    if (found.isEmpty() || !found.get().enabled || found.get().role.equals("SCHEDULER"))
      return Map.of("accepted", true);
    var u = users.lockById(found.get().id).orElseThrow();
    if (tokens.countByUserIdAndCreatedAtAfter(u.id, Instant.now().minusSeconds(3600)) >= 3)
      throw new BusinessException("RATE_LIMITED", "Vượt số lần khôi phục trong giờ.", 429);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var r = new PasswordResetToken();
    r.userId = u.id;
    r.tokenHash = hash(token);
    r.expiresAt = Instant.now().plusSeconds(900);
    tokens.saveAndFlush(r);
    delivery.reset(u.username, token);
    return delivery.local()
        ? Map.of("accepted", true, "localResetToken", token, "mode", "LOCAL_ONLY")
        : Map.of("accepted", true);
  }

  @Transactional
  public void reset(String token, String password) {
    BusinessException.require(
        password.length() >= 12 && password.length() <= 200, "Mật khẩu cần 12–200 ký tự.");
    var userId =
        tokens
            .userIdByHash(hash(token))
            .orElseThrow(
                () ->
                    new BusinessException(
                        "INVALID_RESET_TOKEN", "Mã khôi phục không hợp lệ hoặc đã hết hạn.", 401));
    var u = users.lockById(userId).orElseThrow();
    var r = tokens.findByTokenHash(hash(token)).orElseThrow();
    if (r.consumed || r.expiresAt.isBefore(Instant.now()))
      throw new BusinessException(
          "INVALID_RESET_TOKEN", "Mã khôi phục không hợp lệ hoặc đã hết hạn.", 401);
    u.passwordHash = passwords.encode(password);
    u.failedAttempts = 0;
    u.lockedUntil = null;
    tokens.findByUserId(u.id).forEach(t -> t.consumed = true);
    auth.logout(u.id);
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
}
