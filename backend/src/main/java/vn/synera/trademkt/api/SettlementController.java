package vn.synera.trademkt.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.trademkt.SettlementFacade;

@RestController
@RequestMapping("/api/v1/trade")
public class SettlementController {
  private final SettlementFacade settlements;
  private final IdempotencyService idem;

  public SettlementController(SettlementFacade settlements, IdempotencyService idem) {
    this.settlements = settlements;
    this.idem = idem;
  }

  public record Request(
      @NotNull BigDecimal amount, @NotBlank String referenceNo, Long attachmentId, String reason) {
    public Request {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/budgets")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('SELLIN_CREATE','TRADE_APPROVE','CREDIT_WRITE')")
  public ApiResponse<?> budgets() {
    return ApiResponse.ok(settlements.budgets());
  }

  @GetMapping("/settlements")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('SELLIN_CREATE','TRADE_APPROVE','CREDIT_WRITE')")
  public ApiResponse<?> list() {
    return ApiResponse.ok(settlements.list());
  }

  @PostMapping("/requests/{id}/settle")
  public ApiResponse<?> settle(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "trade.settle." + id,
            r,
            () ->
                settlements.settle(id, r.amount(), r.referenceNo(), r.attachmentId(), r.reason())));
  }
}
