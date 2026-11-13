package vn.synera.iam.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class RoleController {
  private final RoleFacade roles;
  private final CatalogFacade catalog;
  private final IdempotencyService idem;

  public RoleController(RoleFacade roles, CatalogFacade catalog, IdempotencyService idem) {
    this.roles = roles;
    this.catalog = catalog;
    this.idem = idem;
  }

  public record Role(
      @NotBlank String code,
      @NotBlank String name,
      @NotBlank String baseRole,
      @NotNull Set<String> authorities,
      boolean active,
      Long version,
      String reason) {
    public Role {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Grant(
      @NotBlank String roleCode,
      @NotBlank String scopeType,
      @NotBlank String scopeId,
      boolean remove,
      String reason) {
    public Grant {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @PostMapping("/roles")
  public ApiResponse<?> save(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Role r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "role.save." + r.code(),
            r,
            () ->
                roles.save(
                    r.code(),
                    r.name(),
                    r.baseRole(),
                    r.authorities(),
                    r.active(),
                    r.version(),
                    r.reason())));
  }

  @GetMapping("/users/{id}/workspaces")
  public ApiResponse<?> grants(@PathVariable Long id) {
    return ApiResponse.ok(roles.workspaces(id));
  }

  @PostMapping("/users/{id}/workspaces")
  public ApiResponse<?> grant(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Grant r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "role.grant." + id,
            r,
            () -> {
              if (r.scopeType().equals("NPP")) catalog.npp(Long.valueOf(r.scopeId()));
              else if (r.scopeType().equals("REGION"))
                BusinessException.require(
                    catalog.npps().stream().anyMatch(n -> n.region().equals(r.scopeId())),
                    "Vùng chưa có trong danh mục NPP.");
              else
                BusinessException.require(
                    r.scopeId().equals("ALL"), "Phạm vi toàn hệ thống phải dùng ALL.");
              return roles.assign(
                  id, r.roleCode(), r.scopeType(), r.scopeId(), r.remove(), r.reason());
            }));
  }
}
