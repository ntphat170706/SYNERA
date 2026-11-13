package vn.synera.iam.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.iam.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  private final ActorFacade actors;
  private final vn.synera.iam.PasswordRecoveryFacade recovery;

  public AuthController(
      AuthService auth, ActorFacade actors, vn.synera.iam.PasswordRecoveryFacade recovery) {
    this.auth = auth;
    this.actors = actors;
    this.recovery = recovery;
  }

  public record Login(
      @NotBlank @Size(max = 255) String username, @NotBlank @Size(max = 200) String password) {}

  public record Otp(@NotNull Long challengeId, @Pattern(regexp = "[0-9]{6}") String code) {}

  public record Refresh(@NotBlank String refreshToken) {}

  public record Password(
      @NotBlank String oldPassword, @NotBlank @Size(min = 12, max = 200) String newPassword) {}

  @PostMapping("/login")
  public ApiResponse<Object> login(@Valid @RequestBody Login r) {
    return ApiResponse.ok(auth.login(r.username(), r.password()));
  }

  @PostMapping("/otp/verify")
  public ApiResponse<Object> otp(@Valid @RequestBody Otp r) {
    return ApiResponse.ok(auth.verify(r.challengeId(), r.code()));
  }

  @PostMapping("/refresh")
  public ApiResponse<Object> refresh(@Valid @RequestBody Refresh r) {
    return ApiResponse.ok(auth.refresh(r.refreshToken()));
  }

  @GetMapping("/me")
  public ApiResponse<?> me() {
    return ApiResponse.ok(actors.current());
  }

  @PostMapping("/logout")
  public ApiResponse<?> logout() {
    auth.logout(actors.current().id());
    return ApiResponse.ok(java.util.Map.of("revoked", true));
  }

  @PostMapping("/password")
  public ApiResponse<?> password(@Valid @RequestBody Password r) {
    auth.password(actors.current().id(), r.oldPassword(), r.newPassword());
    return ApiResponse.ok(java.util.Map.of("changed", true));
  }

  public record Forgot(@NotBlank @Email String username) {}

  public record Reset(
      @NotBlank String token, @NotBlank @Size(min = 12, max = 200) String newPassword) {}

  @PostMapping("/forgot-password")
  public ApiResponse<?> forgot(@Valid @RequestBody Forgot r) {
    return ApiResponse.ok(recovery.request(r.username()));
  }

  @PostMapping("/reset-password")
  public ApiResponse<?> reset(@Valid @RequestBody Reset r) {
    recovery.reset(r.token(), r.newPassword());
    return ApiResponse.ok(java.util.Map.of("changed", true, "tokensRevoked", true));
  }
}
