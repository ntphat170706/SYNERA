package vn.synera.performance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.ClosingService;
import vn.synera.common.*;
import vn.synera.performance.domain.Target;
import vn.synera.performance.repo.TargetRepository;

@RestController
@RequestMapping("/api/v1/performance")
@PreAuthorize("hasAuthority('REPORT_READ') and !hasRole('SALES')")
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class PerformanceController {
  private final TargetRepository targets;
  private final CatalogFacade catalog;
  private final ClosingService closing;
  private final IdempotencyService idem;
  private final vn.synera.iam.ActorFacade actors;
  private final vn.synera.performance.RewardFacade rewards;

  public PerformanceController(
      TargetRepository targets,
      CatalogFacade catalog,
      ClosingService closing,
      IdempotencyService idem,
      vn.synera.iam.ActorFacade actors,
      vn.synera.performance.RewardFacade rewards) {
    this.targets = targets;
    this.catalog = catalog;
    this.closing = closing;
    this.idem = idem;
    this.actors = actors;
    this.rewards = rewards;
  }

  public record TargetReq(
      @NotNull Long nppId,
      @NotNull Long periodId,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal rewardPct) {}

  @PostMapping("/targets")
  @PreAuthorize("hasAuthority('ORDER_APPROVE') or hasAuthority('TRADE_APPROVE')")
  public ApiResponse<?> target(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TargetReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "target.create",
            r,
            () -> {
              catalog.npp(r.nppId());
              var t = new Target();
              t.nppId = r.nppId();
              t.periodId = r.periodId();
              t.amount = r.amount();
              t.rewardPct = r.rewardPct();
              t.createdBy = actors.current().id();
              targets.saveAndFlush(t);
              return Map.of("id", t.id);
            }));
  }

  @GetMapping("/targets")
  public ApiResponse<?> targets(@RequestParam Long periodId) {
    var ids = catalog.visibleNppIds();
    var snapshots = closing.snapshots(periodId);
    return ApiResponse.ok(
        targets
            .findAll(
                (r, q, cb) -> cb.and(r.get("nppId").in(ids), cb.equal(r.get("periodId"), periodId)))
            .stream()
            .map(
                t -> {
                  var snapshot =
                      snapshots.stream().filter(s -> t.nppId.equals(s.get("nppId"))).findFirst();
                  BigDecimal closed =
                      snapshot.map(s -> (BigDecimal) s.get("amount")).orElse(BigDecimal.ZERO);
                  return Map.of(
                      "id",
                      t.id,
                      "nppId",
                      t.nppId,
                      "target",
                      t.amount,
                      "closedAmount",
                      closed,
                      "reward",
                      snapshot.isPresent() && closed.compareTo(t.amount) >= 0
                          ? closed
                              .multiply(t.rewardPct)
                              .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                          : BigDecimal.ZERO,
                      "layer",
                      "CLOSED_SNAPSHOT_ONLY",
                      "hasSnapshot",
                      snapshot.isPresent());
                })
            .toList());
  }

  public record Payout(@NotBlank String referenceNo) {}

  @GetMapping("/payouts")
  public ApiResponse<?> payouts() {
    return ApiResponse.ok(rewards.list());
  }

  @PostMapping("/targets/{id}/payout")
  public ApiResponse<?> payout(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Payout r) {
    return ApiResponse.ok(
        idem.execute(key, "reward.payout." + id, r, () -> rewards.pay(id, r.referenceNo())));
  }
}
