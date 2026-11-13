package vn.synera.iam.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.*;
import vn.synera.iam.domain.AppUser;
import vn.synera.iam.repo.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {
  private final AppUserRepository users;
  private final PasswordEncoder passwords;
  private final ActorFacade actors;
  private final CatalogFacade catalog;
  private final AuditService audit;
  private final IdempotencyService idem;
  private final JdbcTemplate jdbc;
  private final RoleFacade roles;

  public UserController(
      AppUserRepository users,
      PasswordEncoder passwords,
      ActorFacade actors,
      CatalogFacade catalog,
      AuditService audit,
      IdempotencyService idem,
      JdbcTemplate jdbc,
      RoleFacade roles) {
    this.users = users;
    this.passwords = passwords;
    this.actors = actors;
    this.catalog = catalog;
    this.audit = audit;
    this.idem = idem;
    this.jdbc = jdbc;
    this.roles = roles;
  }

  public record UserReq(
      @NotBlank @Email String username,
      @NotBlank String name,
      @NotBlank @Size(min = 12, max = 200) String password,
      @NotBlank String role,
      Long nppId,
      String region) {}

  public record Access(
      @NotBlank String role, Long nppId, String region, boolean enabled, String reason) {
    public Access {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/users")
  public ApiResponse<?> users() {
    return ApiResponse.ok(
        users.findAll().stream()
            .map(
                u ->
                    Map.of(
                        "id",
                        u.id,
                        "username",
                        u.username,
                        "name",
                        u.displayName,
                        "role",
                        u.role,
                        "nppId",
                        Objects.requireNonNullElse(u.nppId, 0L),
                        "enabled",
                        u.enabled))
            .toList());
  }

  @GetMapping("/roles")
  public ApiResponse<?> roles() {
    return ApiResponse.ok(roles.definitions());
  }

  @PostMapping("/users")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody UserReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "user.create",
            r,
            () -> {
              validateRole(r.role(), r.nppId(), r.region());
              var u = new AppUser();
              u.username = r.username().toLowerCase(Locale.ROOT);
              u.displayName = r.name();
              u.passwordHash = passwords.encode(r.password());
              u.role = r.role();
              u.nppId = r.nppId();
              u.region = r.region();
              u.enabled = true;
              u.otpRequired =
                  Set.of("SYSTEM_ADMIN", "ACCOUNTANT_NSX", "REGION_MANAGER").contains(u.role);
              users.saveAndFlush(u);
              audit.add(actors.current().id(), "User", u.id, "CREATE", Map.of("role", u.role));
              return Map.of("id", u.id);
            }));
  }

  @PostMapping("/users/{id}/access")
  public ApiResponse<?> access(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Access r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "user.access." + id,
            r,
            () -> {
              BusinessException.require(
                  !id.equals(actors.current().id()),
                  "Không tự đổi quyền/khóa tài khoản đang dùng.");
              validateRole(r.role(), r.nppId(), r.region());
              var u = users.lockById(id).orElseThrow(BusinessException::missing);
              u.role = r.role();
              u.nppId = r.nppId();
              u.region = r.region();
              u.enabled = r.enabled();
              u.tokenVersion++;
              jdbc.update("delete from user_role_scope where user_id=?", id);
              jdbc.update(
                  "update refresh_token set revoked_at=CURRENT_TIMESTAMP where user_id=? and"
                      + " revoked_at is null",
                  id);
              u.otpRequired =
                  Set.of("SYSTEM_ADMIN", "ACCOUNTANT_NSX", "REGION_MANAGER").contains(u.role);
              audit.add(
                  actors.current().id(),
                  "User",
                  id,
                  "ACCESS_UPDATE",
                  Map.of("role", u.role, "reason", r.reason()));
              return Map.of("id", id, "tokensRevoked", true);
            }));
  }

  private void validateRole(String role, Long nppId, String region) {
    BusinessException.require(
        roles.exists(role) && !role.equals("SCHEDULER"),
        "Vai trò không tồn tại hoặc chỉ dùng nội bộ.");
    String base = roles.baseRole(role);
    if (!Permissions.nsx(base))
      BusinessException.require(
          nppId != null && (region == null || region.isBlank()),
          "Vai trò NPP/Sales phải có NPP và không dùng phạm vi vùng.");
    else BusinessException.require(nppId == null, "Vai trò NSX dùng phạm vi toàn quốc hoặc vùng.");
    if (base.equals("SYSTEM_ADMIN"))
      BusinessException.require(
          region == null || region.isBlank(), "Quản trị hệ thống phải có phạm vi toàn quốc.");
    if (nppId != null) catalog.npp(nppId);
  }

  @GetMapping("/audit")
  public ApiResponse<?> audit(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    BusinessException.require(page >= 0 && size >= 1 && size <= 100, "Phân trang không hợp lệ.");
    return ApiResponse.ok(
        jdbc.queryForList(
            "select * from audit_log order by id desc limit ? offset ?",
            size,
            Math.multiplyExact(page, size)));
  }

  @GetMapping("/jobs")
  public ApiResponse<?> jobs() {
    return ApiResponse.ok(
        jdbc.queryForList("select * from job_run_log order by id desc limit 100"));
  }

  @GetMapping("/outbox")
  public ApiResponse<?> outbox() {
    return ApiResponse.ok(
        jdbc.queryForList(
            "select id,event_id,event_type,status,attempts,created_at,last_error from"
                + " domain_event_outbox order by id desc limit 100"));
  }

  @PostMapping("/outbox/{id}/retry")
  public ApiResponse<?> retry(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody vn.synera.common.DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "outbox.retry." + id,
            r,
            () -> {
              var rows =
                  jdbc.queryForList(
                      "select id,status from domain_event_outbox where id=? for update", id);
              if (rows.isEmpty()) throw BusinessException.missing();
              BusinessException.require(
                  rows.get(0).get("status").equals("DEAD"),
                  "Chỉ gửi lại sự kiện DEAD sau khi đã sửa nguyên nhân.");
              jdbc.update(
                  "update domain_event_outbox set"
                      + " status='PENDING',attempts=0,last_error=null,available_at=? where id=?",
                  java.sql.Timestamp.from(java.time.Instant.now()),
                  id);
              audit.add(
                  actors.current().id(),
                  "Outbox",
                  id,
                  "RETRY",
                  java.util.Map.of("reason", r.reason()));
              return java.util.Map.of("id", id, "status", "PENDING");
            }));
  }
}
