package vn.synera.salesorder.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.salesorder.ReplacementFacade;

@RestController
@RequestMapping("/api/v1/sales-replacements")
public class ReplacementController {
  private final ReplacementFacade service;
  private final IdempotencyService idem;

  public ReplacementController(ReplacementFacade service, IdempotencyService idem) {
    this.service = service;
    this.idem = idem;
  }

  public record Create(
      @NotNull Long orderId,
      @NotEmpty Map<Long, BigDecimal> quantities,
      @NotNull Instant dueAt,
      @Size(max = 2000) String reason) {
    public Create {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Transition(Map<Long, BigDecimal> quantities, @Size(max = 2000) String reason) {
    public Transition {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Feedback(
      @NotBlank @Size(max = 2000) String note, @NotBlank String expectedStatus) {}

  @GetMapping
  public ApiResponse<?> list(@RequestParam(required = false) Long orderId) {
    return ApiResponse.ok(service.list(orderId));
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable Long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('SALES_CREATE','SALES_APPROVE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Create r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "replacement.create",
            r,
            () -> service.create(r.orderId(), r.quantities(), r.dueAt(), r.reason())));
  }

  @PostMapping("/{id}/{action:approve|ship|receipt|cancel}")
  public ApiResponse<?> transition(
      @PathVariable Long id,
      @PathVariable String action,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Transition r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "replacement." + action + "." + id,
            r,
            () -> service.transition(id, action, r.quantities(), r.reason())));
  }

  @PostMapping("/{id}/customer-feedback")
  @PreAuthorize("hasAnyAuthority('CRM_WRITE','SALES_CREATE')")
  public ApiResponse<?> feedback(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Feedback r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "replacement.feedback." + id,
            r,
            () -> service.feedback(id, r.note(), r.expectedStatus())));
  }
}
