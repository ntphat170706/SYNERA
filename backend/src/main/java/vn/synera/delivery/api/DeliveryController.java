package vn.synera.delivery.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.delivery.DeliveryFacade;

@RestController
@RequestMapping("/api/v1/delivery-trips")
@PreAuthorize("hasAnyAuthority('WAREHOUSE_WRITE','DELIVERY_EXECUTE')")
public class DeliveryController {
  private final DeliveryFacade delivery;
  private final IdempotencyService idem;

  public DeliveryController(DeliveryFacade delivery, IdempotencyService idem) {
    this.delivery = delivery;
    this.idem = idem;
  }

  public record Create(
      @NotNull Long nppId,
      @NotNull Long driverId,
      @NotNull LocalDate deliveryDate,
      @NotEmpty List<DeliveryFacade.Stop> stops) {}

  public record Receipt(
      @NotNull Long proofAttachmentId, Map<Long, BigDecimal> quantities, String note) {
    public Receipt {
      note = java.util.Objects.toString(note, "");
    }
  }

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(delivery.list());
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable Long id) {
    return ApiResponse.ok(delivery.detail(id));
  }

  @PostMapping
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Create r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "delivery.create",
            r,
            () -> delivery.create(r.nppId(), r.driverId(), r.deliveryDate(), r.stops())));
  }

  @PostMapping("/{id}/dispatch")
  public ApiResponse<?> dispatch(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(key, "delivery.dispatch." + id, Map.of(), () -> delivery.dispatch(id)));
  }

  @PostMapping("/{id}/stops/{stopId}/deliver")
  public ApiResponse<?> deliver(
      @PathVariable Long id,
      @PathVariable Long stopId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Receipt r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "delivery.stop." + stopId,
            r,
            () -> delivery.deliver(id, stopId, r.proofAttachmentId(), r.quantities(), r.note())));
  }
}
