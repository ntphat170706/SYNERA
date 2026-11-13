package vn.synera.closing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.closing.*;
import vn.synera.common.*;

@RestController
@RequestMapping("/api/v1/closing")
public class ClosingController {
  private final ClosingService closing;
  private final ReconciliationFacade reconciliation;
  private final IdempotencyService idem;

  public ClosingController(
      ClosingService closing, ReconciliationFacade reconciliation, IdempotencyService idem) {
    this.closing = closing;
    this.reconciliation = reconciliation;
    this.idem = idem;
  }

  public record Period(
      @NotBlank String type, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

  public record Transition(@NotBlank String status, String reason) {
    public Transition {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record AdjustmentReq(
      @NotNull Long originalPeriodId,
      @NotNull Long nppId,
      @NotNull BigDecimal delta,
      String reason) {
    public AdjustmentReq {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Approve(@NotNull Long postedPeriodId) {}

  @GetMapping("/periods")
  public ApiResponse<?> periods() {
    return ApiResponse.ok(closing.list());
  }

  @PostMapping("/periods")
  @PreAuthorize("hasAuthority('CLOSING_WRITE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Period r) {
    return ApiResponse.ok(
        idem.execute(
            key, "period.create", r, () -> closing.create(r.type(), r.startDate(), r.endDate())));
  }

  @PostMapping("/periods/{id}/transition")
  @PreAuthorize("hasAuthority('CLOSING_WRITE')")
  public ApiResponse<?> advance(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Transition r) {
    return ApiResponse.ok(
        idem.execute(
            key, "period.advance." + id, r, () -> closing.advance(id, r.status(), r.reason())));
  }

  @GetMapping("/periods/{id}/snapshots")
  @PreAuthorize("!hasRole('SALES') and !hasRole('DRIVER') and !hasRole('STORE')")
  public ApiResponse<?> snapshot(@PathVariable Long id) {
    return ApiResponse.ok(closing.snapshots(id));
  }

  @GetMapping("/exceptions")
  public ApiResponse<?> exceptions() {
    return ApiResponse.ok(reconciliation.list());
  }

  @PostMapping("/exceptions/{id}/resolve")
  @PreAuthorize("hasAuthority('CLOSING_WRITE')")
  public ApiResponse<?> resolve(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(
            key, "reconciliation.resolve." + id, r, () -> reconciliation.resolve(id, r.reason())));
  }

  @PostMapping("/adjustments")
  @PreAuthorize("hasAuthority('CLOSING_WRITE')")
  public ApiResponse<?> adjust(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody AdjustmentReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "adjustment.create",
            r,
            () -> closing.adjustment(r.originalPeriodId(), r.nppId(), r.delta(), r.reason())));
  }

  @GetMapping("/adjustments")
  public ApiResponse<?> adjustments() {
    return ApiResponse.ok(closing.adjustments());
  }

  @PostMapping("/adjustments/{id}/approve")
  @PreAuthorize("hasAuthority('CLOSING_WRITE')")
  public ApiResponse<?> approve(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Approve r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "adjustment.approve." + id,
            r,
            () -> closing.approveAdjustment(id, r.postedPeriodId())));
  }
}
