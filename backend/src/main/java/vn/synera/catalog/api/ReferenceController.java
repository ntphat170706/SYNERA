package vn.synera.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.ReferenceFacade;
import vn.synera.common.*;

@RestController
@RequestMapping("/api/v1/catalog/references")
public class ReferenceController {
  private final ReferenceFacade references;
  private final IdempotencyService idem;

  public ReferenceController(ReferenceFacade references, IdempotencyService idem) {
    this.references = references;
    this.idem = idem;
  }

  public record Request(
      @NotBlank String kind,
      @NotBlank String code,
      @NotBlank String name,
      Long parentId,
      Long nppId,
      boolean active,
      Long version,
      String reason) {
    public Request {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Members(
      @NotNull java.util.List<@NotNull Long> memberIds, String reason, boolean remove) {
    public Members {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/{id}/members")
  public ApiResponse<?> members(@PathVariable Long id) {
    return ApiResponse.ok(references.members(id));
  }

  @PostMapping("/{id}/members")
  public ApiResponse<?> assignMembers(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Members r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "reference.members." + id,
            r,
            () -> references.assignMembers(id, r.memberIds(), r.reason(), r.remove())));
  }

  @GetMapping
  public ApiResponse<?> list(@RequestParam String kind) {
    return ApiResponse.ok(references.list(kind));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('CATALOG_WRITE') or hasAuthority('CRM_WRITE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return save(null, key, r);
  }

  @PostMapping("/{id}")
  @PreAuthorize("hasAuthority('CATALOG_WRITE') or hasAuthority('CRM_WRITE')")
  public ApiResponse<?> update(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Request r) {
    return save(id, key, r);
  }

  private ApiResponse<?> save(Long id, String key, Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "reference.save." + id,
            r,
            () ->
                references.save(
                    id,
                    r.kind(),
                    r.code(),
                    r.name(),
                    r.parentId(),
                    r.nppId(),
                    r.active(),
                    r.version(),
                    r.reason())));
  }
}
