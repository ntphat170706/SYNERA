package vn.synera.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.inventory.StocktakeFacade;

@RestController
@RequestMapping("/api/v1/inventory/stocktakes")
@PreAuthorize("hasAuthority('WAREHOUSE_WRITE')")
public class StocktakeController {
  private final StocktakeFacade takes;
  private final IdempotencyService idem;

  public StocktakeController(StocktakeFacade takes, IdempotencyService idem) {
    this.takes = takes;
    this.idem = idem;
  }

  public record Request(
      @NotNull Long warehouseId, @NotEmpty Map<Long, BigDecimal> counted, String reason) {
    public Request {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping
  public ApiResponse<?> list(@RequestParam Long warehouseId) {
    return ApiResponse.ok(takes.list(warehouseId));
  }

  @PostMapping
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "stocktake.create",
            r,
            () -> takes.create(r.warehouseId(), r.counted(), r.reason())));
  }

  @PostMapping("/{id}/approve")
  public ApiResponse<?> approve(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "stocktake.approve." + id, r, () -> takes.approve(id, r.reason())));
  }
}
