package vn.synera.iam.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.iam.*;

/** Browser access tokens stay in memory; refresh credentials stay in an HttpOnly cookie. */
@RestController
@RequestMapping("/api/v1/auth/session")
public class BrowserSessionController {
  private final AuthService auth;
  private final ActorFacade actors;
  private final Set<String> origins;
  private final boolean secure;
  private final int refreshDays;
  private final RoleFacade roles;

  public BrowserSessionController(
      AuthService auth,
      ActorFacade actors,
      @Value("${synera.cors-origins}") String origins,
      @Value("${synera.session.secure-cookie:true}") boolean secure,
      @Value("${synera.jwt.refresh-days}") int refreshDays,
      RoleFacade roles) {
    this.auth = auth;
    this.actors = actors;
    this.origins = new HashSet<>(Arrays.asList(origins.split(",")));
    this.secure = secure;
    this.refreshDays = refreshDays;
    this.roles = roles;
  }

  private void browser(HttpServletRequest request) {
    // A custom header forces CORS preflight. An explicit origin allowlist also prevents login CSRF.
    if (!"synera".equals(request.getHeader("X-Session-Request"))
        || !origins.contains(request.getHeader("Origin")))
      throw new BusinessException(
          "FORBIDDEN_ORIGIN", "Yêu cầu phiên không đến từ website được cho phép.", 403);
  }

  private void cookie(HttpServletResponse response, String value, int days) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from("synera_refresh", value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/api/v1/auth/session")
            .maxAge(Duration.ofDays(days))
            .build()
            .toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  private ApiResponse<?> result(Object result, HttpServletResponse response) {
    var data = new LinkedHashMap<String, Object>((Map<String, Object>) result);
    Object refresh = data.remove("refreshToken");
    if (refresh != null) cookie(response, refresh.toString(), refreshDays);
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    return ApiResponse.ok(data);
  }

  @PostMapping("/login")
  public ApiResponse<?> login(
      @Valid @RequestBody AuthController.Login r, HttpServletRequest req, HttpServletResponse res) {
    browser(req);
    return result(auth.login(r.username(), r.password()), res);
  }

  @PostMapping("/otp")
  public ApiResponse<?> otp(
      @Valid @RequestBody AuthController.Otp r, HttpServletRequest req, HttpServletResponse res) {
    browser(req);
    return result(auth.verify(r.challengeId(), r.code()), res);
  }

  @PostMapping("/refresh")
  public ApiResponse<?> refresh(
      @CookieValue(name = "synera_refresh", required = false) String token,
      HttpServletRequest req,
      HttpServletResponse res) {
    browser(req);
    if (token == null || token.isBlank())
      throw new BusinessException("UNAUTHENTICATED", "Chưa có phiên đăng nhập.", 401);
    try {
      return result(auth.refresh(token), res);
    } catch (BusinessException e) {
      cookie(res, "", 0);
      throw e;
    }
  }

  public record Workspace(@jakarta.validation.constraints.NotBlank String key) {}

  @GetMapping("/workspaces")
  public ApiResponse<?> workspaces() {
    return ApiResponse.ok(roles.workspaces(actors.current().id()));
  }

  @PostMapping("/workspace")
  public ApiResponse<?> workspace(
      @Valid @RequestBody Workspace r, HttpServletRequest req, HttpServletResponse res) {
    browser(req);
    return result(auth.switchWorkspace(actors.current().id(), r.key()), res);
  }

  @PostMapping("/logout")
  public ApiResponse<?> logout(
      @CookieValue(name = "synera_refresh", required = false) String token,
      HttpServletRequest req,
      HttpServletResponse res) {
    browser(req);
    auth.logoutRefresh(token);
    cookie(res, "", 0);
    return ApiResponse.ok(Map.of("revoked", true));
  }
}
