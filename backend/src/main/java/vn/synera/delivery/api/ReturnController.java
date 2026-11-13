package vn.synera.delivery.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.delivery.ReturnFacade;

@RestController
@RequestMapping("/api/v1/returns")
public class ReturnController {
  private final ReturnFacade returns;
  private final IdempotencyService idem;

  public ReturnController(ReturnFacade returns, IdempotencyService idem) {
    this.returns = returns;
    this.idem = idem;
  }

  public record Request(
      @NotNull Long orderId, @NotEmpty Map<Long, BigDecimal> quantities, String reason) {
    public Request {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping
  @PreAuthorize("hasAuthority('SELLIN_CREATE') or hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> list() {
    return ApiResponse.ok(returns.list());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SELLIN_CREATE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "return.create",
            r,
            () -> returns.create(r.orderId(), r.quantities(), r.reason())));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> approve(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "return.approve." + id, r, () -> returns.approve(id, r.reason())));
  }

  @PostMapping("/{id}/receive")
  @PreAuthorize("hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> receive(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(key, "return.receive." + id, Map.of(), () -> returns.receive(id)));
  }
}
