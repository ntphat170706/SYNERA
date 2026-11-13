package vn.synera.policy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.policy.*;

@RestController
@RequestMapping("/api/v1")
public class PolicyController {
  private final PricingFacade pricing;
  private final IdempotencyService idem;

  public PolicyController(PricingFacade pricing, IdempotencyService idem) {
    this.pricing = pricing;
    this.idem = idem;
  }

  public record PromotionReq(
      @NotBlank String name,
      Long nppId,
      Long skuId,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percent,
      @NotNull @DecimalMin("0") BigDecimal budget,
      @NotNull @DecimalMin("0") BigDecimal minQty,
      @NotNull @DecimalMin("0") BigDecimal minAmount,
      int priority,
      String exclusionGroup,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate) {}

  public record Conditions(
      @NotNull Map<Long, BigDecimal> requiredBase, Long maxUses, String reason) {
    public Conditions {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @PostMapping("/policies/{id}/conditions")
  @PreAuthorize("hasAuthority('POLICY_WRITE')")
  public ApiResponse<?> conditions(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Conditions r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "policy.conditions." + id,
            r,
            () -> pricing.configureConditions(id, r.requiredBase(), r.maxUses(), r.reason())));
  }

  @PostMapping("/pricing/quote")
  public ApiResponse<?> quote(@Valid @RequestBody DocumentRequests.Order r) {
    return ApiResponse.ok(
        pricing.quote(r.nppId(), r.storeId(), r.lines(), vn.synera.common.Dates.today(), false));
  }

  public record PriceReq(
      Long nppId,
      @NotNull Long skuId,
      @NotBlank String uom,
      @NotNull @DecimalMin("0") BigDecimal price,
      @NotNull LocalDate validFrom,
      @NotNull LocalDate validTo,
      Long nppGroupId,
      Long storeGroupId,
      String reason) {
    public PriceReq {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/pricing/prices")
  @PreAuthorize("hasAnyAuthority('POLICY_WRITE','SELLIN_CREATE')")
  public ApiResponse<?> prices(@RequestParam(required = false) Long nppId) {
    return ApiResponse.ok(pricing.prices(nppId));
  }

  @PostMapping("/pricing/prices")
  @PreAuthorize("hasAuthority('POLICY_WRITE')")
  public ApiResponse<?> price(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PriceReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "price.publish",
            r,
            () ->
                pricing.addPrice(
                    r.nppId(),
                    r.skuId(),
                    r.uom(),
                    r.price(),
                    r.validFrom(),
                    r.validTo(),
                    r.reason(),
                    r.nppGroupId(),
                    r.storeGroupId())));
  }

  @GetMapping("/catalog/offerings")
  public ApiResponse<?> offerings(
      @RequestParam Long nppId, @RequestParam(required = false) Long storeId) {
    return ApiResponse.ok(pricing.offerings(nppId, storeId));
  }

  @GetMapping("/policies")
  @PreAuthorize("hasAnyAuthority('POLICY_WRITE','SELLIN_CREATE','ORDER_APPROVE')")
  public ApiResponse<?> list() {
    return ApiResponse.ok(pricing.policies());
  }

  @PostMapping("/policies")
  @PreAuthorize("hasAuthority('POLICY_WRITE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PromotionReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "policy.create",
            r,
            () ->
                pricing.create(
                    r.name(),
                    r.nppId(),
                    r.skuId(),
                    r.percent(),
                    r.budget(),
                    r.minQty(),
                    r.minAmount(),
                    r.priority(),
                    r.exclusionGroup(),
                    r.startDate(),
                    r.endDate())));
  }

  @PostMapping("/policies/{id}/publish")
  @PreAuthorize("hasAuthority('POLICY_WRITE')")
  public ApiResponse<?> publish(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "policy.publish." + id, r, () -> pricing.publish(id, r.reason())));
  }

  public record Targets(Long nppGroupId, Long storeGroupId, String reason) {
    public Targets {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @PostMapping("/policies/{id}/targets")
  @PreAuthorize("hasAuthority('POLICY_WRITE')")
  public ApiResponse<?> targets(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Targets r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "policy.targets." + id,
            r,
            () -> pricing.targetGroups(id, r.nppGroupId(), r.storeGroupId(), r.reason())));
  }
}
