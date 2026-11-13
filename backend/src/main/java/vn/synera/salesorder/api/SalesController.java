package vn.synera.salesorder.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.salesorder.*;

@RestController
@RequestMapping("/api/v1/sales-orders")
@Validated
public class SalesController {
  private final SalesFacade sales;
  private final SalesSyncWorker sync;
  private final IdempotencyService idem;
  private final jakarta.validation.Validator validator;

  public SalesController(
      SalesFacade sales,
      SalesSyncWorker sync,
      IdempotencyService idem,
      jakarta.validation.Validator validator) {
    this.sales = sales;
    this.sync = sync;
    this.idem = idem;
    this.validator = validator;
  }

  public record SyncLine(
      @NotNull Long skuId,
      @NotBlank String uom,
      @NotNull @DecimalMin("0.001") BigDecimal qty,
      @NotNull @DecimalMin("0") BigDecimal clientPrice) {}

  public record SyncItem(
      @NotBlank @Size(max = 100) String clientId,
      @NotBlank @Size(min = 8, max = 100) String idempotencyKey,
      @NotNull Long storeId,
      @NotNull Instant createdAt,
      @NotEmpty @Size(max = 100) List<@Valid SyncLine> lines) {}

  public record Create(@NotNull Long storeId, @NotEmpty @Valid List<DocumentRequests.Line> lines) {}

  public record Transition(Map<Long, BigDecimal> quantities, String reason) {
    public Transition {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping
  public ApiResponse<?> list(@RequestParam(required = false) Long storeId) {
    return ApiResponse.ok(sales.list(storeId));
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable Long id) {
    return ApiResponse.ok(sales.detail(id));
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAuthority('REPORT_READ') and hasRole('SALES')")
  public ApiResponse<?> summary(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return ApiResponse.ok(sales.personalSummary(from, to));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SALES_CREATE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Create r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "sales.create",
            r,
            () -> sales.create(r.storeId(), key, key, Instant.now(), r.lines())));
  }

  @PostMapping("/sync")
  @PreAuthorize("hasAuthority('SALES_CREATE')")
  public ApiResponse<?> sync(@RequestBody @NotEmpty @Size(max = 50) List<SyncItem> items) {
    var results = new ArrayList<Object>();
    for (var item : items) {
      if (item == null || !validator.validate(item).isEmpty()) {
        results.add(
            Map.of(
                "clientId",
                item == null ? "" : java.util.Objects.toString(item.clientId(), ""),
                "status",
                "REJECTED",
                "code",
                "VALIDATION_FAILED",
                "message",
                "Đơn có trường dữ liệu không hợp lệ."));
        continue;
      }
      try {
        results.add(sync.sync(item));
      } catch (BusinessException e) {
        results.add(
            Map.of(
                "clientId",
                item.clientId(),
                "status",
                "REJECTED",
                "message",
                e.getMessage(),
                "code",
                e.code));
      } catch (org.springframework.dao.DataIntegrityViolationException e) {
        results.add(
            Map.of(
                "clientId",
                item.clientId(),
                "status",
                "REJECTED",
                "message",
                "Mã client hoặc dữ liệu trùng, cần kiểm tra lại.",
                "code",
                "DUPLICATE_REQUEST"));
      }
    }
    return ApiResponse.ok(results);
  }

  @PostMapping("/{id}/{action:confirm|ship|dispatch|receipt|cancel}")
  @PreAuthorize("hasAuthority('SALES_APPROVE')")
  public ApiResponse<?> transition(
      @PathVariable Long id,
      @PathVariable String action,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Transition r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "sales." + action + "." + id,
            r,
            () -> sales.transition(id, action, r.quantities(), r.reason())));
  }
}
