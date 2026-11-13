package vn.synera.sellin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.sellin.SellInFacade;

@RestController
@RequestMapping("/api/v1/sell-in-orders")
public class SellInController {
  private final SellInFacade service;
  private final IdempotencyService idem;

  public SellInController(SellInFacade service, IdempotencyService idem) {
    this.service = service;
    this.idem = idem;
  }

  public record Confirm(@NotEmpty Map<Long, BigDecimal> quantities) {}

  @GetMapping
  @PreAuthorize("!hasRole('SALES') and !hasRole('DRIVER') and !hasRole('STORE')")
  public ApiResponse<?> list(@RequestParam(required = false) String status) {
    return ApiResponse.ok(service.list(status));
  }

  @GetMapping("/{id}")
  @PreAuthorize("!hasRole('SALES') and !hasRole('DRIVER') and !hasRole('STORE')")
  public ApiResponse<?> detail(@PathVariable Long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SELLIN_CREATE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody DocumentRequests.Order r) {
    return ApiResponse.ok(idem.execute(key, "sellin.create", r, () -> service.create(r, key)));
  }

  @PostMapping("/{id}/submit")
  @PreAuthorize("hasAuthority('SELLIN_CREATE')")
  public ApiResponse<?> submit(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.submit." + id, Map.of(), () -> service.submit(id)));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> approve(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody(required = false) DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "sellin.approve." + id,
            Map.of("reason", r == null ? "" : Objects.toString(r.reason(), "")),
            () -> service.decision(id, "approve", r == null ? null : r.reason())));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> reject(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(
            key, "sellin.reject." + id, r, () -> service.decision(id, "reject", r.reason())));
  }

  @PostMapping("/{id}/need-info")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> needInfo(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(
            key, "sellin.need-info." + id, r, () -> service.decision(id, "need-info", r.reason())));
  }

  @PostMapping("/{id}/resubmit")
  @PreAuthorize("hasAuthority('SELLIN_CREATE')")
  public ApiResponse<?> resubmit(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.resubmit." + id, r, () -> service.resubmit(id, r.reason())));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyAuthority('SELLIN_CREATE','ORDER_APPROVE')")
  public ApiResponse<?> cancel(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.cancel." + id, r, () -> service.cancel(id, r.reason())));
  }

  @PostMapping("/{id}/confirm")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> confirm(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Confirm r) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.confirm." + id, r, () -> service.confirm(id, r.quantities())));
  }

  @PostMapping("/{id}/ship")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> ship(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(idem.execute(key, "sellin.ship." + id, Map.of(), () -> service.ship(id)));
  }

  @PostMapping("/{id}/dispatch")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> dispatch(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(
            key, "sellin.dispatch." + id, Map.of(), () -> service.delivery(id, "dispatch")));
  }

  @PostMapping("/{id}/delivered")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> delivered(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(
            key, "sellin.delivered." + id, Map.of(), () -> service.delivery(id, "delivered")));
  }

  @PostMapping("/{id}/receipt")
  @PreAuthorize("hasAnyAuthority('SELLIN_CREATE','WAREHOUSE_WRITE','ORDER_APPROVE')")
  public ApiResponse<?> receipt(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Receipt r) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.receipt." + id, r, () -> service.receive(id, r)));
  }

  @PostMapping("/{id}/reconcile")
  @PreAuthorize("hasAuthority('CLOSING_WRITE')")
  public ApiResponse<?> reconcile(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.reconcile." + id, r, () -> service.reconcile(id, r.reason())));
  }

  @PostMapping("/{id}/reorder")
  @PreAuthorize("hasAuthority('SELLIN_CREATE')")
  public ApiResponse<?> reorder(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.reorder." + id, Map.of(), () -> service.reorder(id, key)));
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('CLOSING_WRITE')")
  public ApiResponse<?> close(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "sellin.close." + id, r, () -> service.close(id, r.reason())));
  }
}
