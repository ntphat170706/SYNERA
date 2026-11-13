package vn.synera.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.inventory.InventoryFacade;

@RestController
@RequestMapping("/api/v1/inventory")
public class TransferController {
  private final InventoryFacade inventory;
  private final CatalogFacade catalog;
  private final JdbcTemplate jdbc;
  private final IdempotencyService idem;

  public TransferController(
      InventoryFacade inventory,
      CatalogFacade catalog,
      JdbcTemplate jdbc,
      IdempotencyService idem) {
    this.inventory = inventory;
    this.catalog = catalog;
    this.jdbc = jdbc;
    this.idem = idem;
  }

  public record Request(
      @NotNull Long sourceWarehouseId,
      @NotNull Long destinationWarehouseId,
      @NotNull Long skuId,
      @NotNull @DecimalMin("0.001") BigDecimal qtyBase,
      String reason) {
    public Request {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/movements")
  public ApiResponse<?> movements(@RequestParam Long warehouseId) {
    return ApiResponse.ok(inventory.movementHistory(warehouseId));
  }

  @GetMapping("/transfers")
  public ApiResponse<?> list(@RequestParam Long warehouseId) {
    catalog.warehouse(warehouseId);
    return ApiResponse.ok(
        jdbc.queryForList(
            "select * from stock_transfer where source_warehouse_id=? or destination_warehouse_id=?"
                + " order by id desc limit 1000",
            warehouseId,
            warehouseId));
  }

  @PostMapping("/transfers")
  @PreAuthorize("hasAuthority('WAREHOUSE_WRITE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "stock.transfer",
            r,
            () ->
                inventory.transfer(
                    r.sourceWarehouseId(),
                    r.destinationWarehouseId(),
                    r.skuId(),
                    r.qtyBase(),
                    r.reason())));
  }
}
