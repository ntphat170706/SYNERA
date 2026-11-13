package vn.synera.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.inventory.InventoryFacade;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
  private final InventoryFacade stock;
  private final IdempotencyService idem;

  public InventoryController(InventoryFacade stock, IdempotencyService idem) {
    this.stock = stock;
    this.idem = idem;
  }

  public record Receipt(
      @NotNull Long warehouseId,
      @NotNull Long skuId,
      @NotBlank String lotNo,
      @NotNull @DecimalMin("0.001") BigDecimal qtyBase,
      @NotNull java.time.LocalDate expiryDate,
      String reason) {
    public Receipt {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/balances")
  public ApiResponse<?> balance(@RequestParam Long warehouseId) {
    return ApiResponse.ok(stock.balances(warehouseId));
  }

  @PostMapping("/receipts")
  @PreAuthorize("hasAuthority('WAREHOUSE_WRITE')")
  public ApiResponse<?> receipt(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Receipt r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "stock.receipt",
            r,
            () ->
                stock.receipt(
                    r.warehouseId(),
                    r.skuId(),
                    r.lotNo(),
                    r.qtyBase(),
                    r.expiryDate(),
                    r.reason())));
  }
}
