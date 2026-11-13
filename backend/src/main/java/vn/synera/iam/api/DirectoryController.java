package vn.synera.iam.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.*;
import vn.synera.iam.domain.AppUser;
import vn.synera.iam.repo.AppUserRepository;

@RestController
@RequestMapping("/api/v1/staff")
public class DirectoryController {
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AppUserRepository users;
  private final PasswordEncoder passwords;
  private final IdempotencyService idem;
  private final AuditService audit;

  public DirectoryController(
      CatalogFacade catalog,
      ActorFacade actors,
      AppUserRepository users,
      PasswordEncoder passwords,
      IdempotencyService idem,
      AuditService audit) {
    this.catalog = catalog;
    this.actors = actors;
    this.users = users;
    this.passwords = passwords;
    this.idem = idem;
    this.audit = audit;
  }

  @GetMapping
  public ApiResponse<?> staff(
      @RequestParam Long nppId, @RequestParam(required = false) String role) {
    catalog.npp(nppId);
    var actor = actors.current();
    return ApiResponse.ok(
        actors.staff(nppId, role).stream()
            .filter(u -> !actor.sales() || u.id().equals(actor.id()))
            .toList());
  }

  public record Create(
      @NotNull Long nppId,
      @NotBlank @Email String username,
      @NotBlank String name,
      @NotBlank @Size(min = 12, max = 200) String password,
      @NotBlank String role) {}

  @PostMapping
  @PreAuthorize("hasAuthority('ROUTE_WRITE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Create r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "staff.create",
            r,
            () -> {
              catalog.npp(r.nppId());
              BusinessException.require(
                  Set.of("SALES", "SUPERVISOR", "WAREHOUSE_NPP", "ACCOUNTANT_NPP", "DRIVER")
                      .contains(r.role()),
                  "Vai trò không được cấp qua danh bạ NPP.");
              BusinessException.require(
                  users.findByUsername(r.username().trim().toLowerCase(Locale.ROOT)).isEmpty(),
                  "Email đã được sử dụng.");
              var u = new AppUser();
              u.username = r.username().trim().toLowerCase(Locale.ROOT);
              u.displayName = r.name();
              u.passwordHash = passwords.encode(r.password());
              u.role = r.role();
              u.nppId = r.nppId();
              u.enabled = true;
              users.saveAndFlush(u);
              audit.add(
                  actors.current().id(),
                  "User",
                  u.id,
                  "STAFF_CREATE",
                  Map.of("role", u.role, "nppId", u.nppId));
              return Map.of("id", u.id);
            }));
  }
}
