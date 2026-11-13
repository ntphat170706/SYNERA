package vn.synera.credit.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.credit.CreditFacade;
import vn.synera.sellin.SellInFacade;

@RestController
@RequestMapping("/api/v1/credit")
public class CreditController {
  private final CreditFacade credit;
  private final SellInFacade orders;
  private final IdempotencyService idem;

  public CreditController(CreditFacade credit, SellInFacade orders, IdempotencyService idem) {
    this.credit = credit;
    this.orders = orders;
    this.idem = idem;
  }

  public record Limit(@NotNull @DecimalMin("0") BigDecimal amount, String reason) {
    public Limit {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Payment(
      @NotNull Long nppId,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotBlank @Size(max = 100) String reference) {}

  public record Temporary(
      @NotNull BigDecimal amount, @NotNull java.time.Instant validUntil, String reason) {
    public Temporary {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Terms(@Min(0) @Max(365) int overdueDaysThreshold, String reason) {
    public Terms {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @PostMapping("/{nppId}/temporary-limit")
  @PreAuthorize("hasAuthority('CREDIT_WRITE')")
  public ApiResponse<?> temporary(
      @PathVariable Long nppId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Temporary r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "credit.temporary." + nppId,
            r,
            () -> credit.temporaryLimit(nppId, r.amount(), r.validUntil(), r.reason())));
  }

  @PostMapping("/{nppId}/terms")
  @PreAuthorize("hasAuthority('CREDIT_WRITE')")
  public ApiResponse<?> terms(
      @PathVariable Long nppId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Terms r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "credit.terms." + nppId,
            r,
            () -> credit.terms(nppId, r.overdueDaysThreshold(), r.reason())));
  }

  @GetMapping
  @PreAuthorize(
      "hasAuthority('CREDIT_WRITE') or hasAuthority('SELLIN_CREATE') or hasAuthority('USER_MANAGE')"
          + " or authentication.token.claims['role']=='ACCOUNTANT_NPP'")
  public ApiResponse<?> list() {
    return ApiResponse.ok(credit.accounts());
  }

  @GetMapping("/{nppId}/ledger")
  @PreAuthorize(
      "hasAuthority('CREDIT_WRITE') or hasAuthority('SELLIN_CREATE') or"
          + " authentication.token.claims['role']=='ACCOUNTANT_NPP'")
  public ApiResponse<?> ledger(@PathVariable Long nppId) {
    return ApiResponse.ok(credit.ledger(nppId));
  }

  @GetMapping("/{nppId}/invoices")
  @PreAuthorize(
      "hasAuthority('CREDIT_WRITE') or hasAuthority('SELLIN_CREATE') or"
          + " authentication.token.claims['role']=='ACCOUNTANT_NPP'")
  public ApiResponse<?> invoices(@PathVariable Long nppId) {
    return ApiResponse.ok(credit.invoices(nppId));
  }

  @PostMapping("/{nppId}/limit")
  @PreAuthorize("hasAuthority('CREDIT_WRITE')")
  public ApiResponse<?> limit(
      @PathVariable Long nppId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Limit r) {
    return ApiResponse.ok(
        idem.execute(
            key, "credit.limit." + nppId, r, () -> credit.limit(nppId, r.amount(), r.reason())));
  }

  @PostMapping("/payments")
  @PreAuthorize("hasAuthority('CREDIT_WRITE')")
  public ApiResponse<?> pay(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Payment r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "credit.payment",
            r,
            () -> {
              var result = credit.pay(r.nppId(), r.amount(), r.reference());
              orders.recheckHeld(r.nppId());
              return result;
            }));
  }
}
