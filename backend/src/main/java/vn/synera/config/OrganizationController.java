package vn.synera.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.credit.CreditFacade;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
  private final CatalogFacade catalog;
  private final CreditFacade credit;
  private final IdempotencyService idem;

  public OrganizationController(
      CatalogFacade catalog, CreditFacade credit, IdempotencyService idem) {
    this.catalog = catalog;
    this.credit = credit;
    this.idem = idem;
  }

  public record Create(
      @NotBlank String code,
      @NotBlank String name,
      @NotBlank String region,
      @NotNull @DecimalMin("0") BigDecimal creditLimit) {}

  public record Node(
      String code,
      @NotBlank String name,
      String type,
      Long parentId,
      @NotBlank String region,
      Long version,
      String reason) {
    public Node {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/tree")
  public ApiResponse<?> tree() {
    return ApiResponse.ok(catalog.organizationTree());
  }

  @PostMapping("/nodes")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ApiResponse<?> node(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Node r) {
    return save(null, key, r);
  }

  @PostMapping("/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ApiResponse<?> update(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Node r) {
    return save(id, key, r);
  }

  private ApiResponse<?> save(Long id, String key, Node r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "organization.tree." + id,
            r,
            () ->
                catalog.saveOrganization(
                    id,
                    r.code(),
                    r.name(),
                    r.type(),
                    r.parentId(),
                    r.region(),
                    r.version(),
                    r.reason())));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Create r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "organization.create",
            r,
            () -> {
              var org = catalog.createNpp(r.code(), r.name(), r.region());
              credit.initialize(org.id(), r.creditLimit());
              return org;
            }));
  }
}
