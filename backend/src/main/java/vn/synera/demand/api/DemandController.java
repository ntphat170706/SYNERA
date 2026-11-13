package vn.synera.demand.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.demand.domain.*;
import vn.synera.demand.repo.*;
import vn.synera.iam.ActorFacade;
import vn.synera.salesorder.SalesFacade;

@RestController
@RequestMapping("/api/v1/demand")
@PreAuthorize("hasAnyAuthority('SELLIN_CREATE','ORDER_APPROVE')")
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class DemandController {
  private final DemandPlanRepository plans;
  private final CatalogFacade catalog;
  private final SalesFacade sales;
  private final ActorFacade actors;
  private final IdempotencyService idem;
  private final Jsons json;
  private final AuditService audit;
  private final vn.synera.inventory.InventoryFacade inventory;
  private final vn.synera.sellin.SellInFacade sellin;

  public DemandController(
      DemandPlanRepository plans,
      CatalogFacade catalog,
      SalesFacade sales,
      ActorFacade actors,
      IdempotencyService idem,
      Jsons json,
      AuditService audit,
      vn.synera.inventory.InventoryFacade inventory,
      vn.synera.sellin.SellInFacade sellin) {
    this.plans = plans;
    this.catalog = catalog;
    this.sales = sales;
    this.actors = actors;
    this.idem = idem;
    this.json = json;
    this.audit = audit;
    this.inventory = inventory;
    this.sellin = sellin;
  }

  public record Plan(
      @NotNull Long nppId,
      @NotNull LocalDate month,
      @NotEmpty @Valid List<DocumentRequests.Line> lines) {}

  public record State(@NotBlank String status, String reason) {
    public State {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/plans")
  public ApiResponse<?> list() {
    var ids = catalog.visibleNppIds();
    return ApiResponse.ok(
        plans.findAll((r, q, cb) -> r.get("nppId").in(ids)).stream()
            .map(
                p ->
                    Map.of(
                        "id",
                        p.id,
                        "nppId",
                        p.nppId,
                        "month",
                        p.month,
                        "status",
                        p.status,
                        "lines",
                        json.read(p.linesJson)))
            .toList());
  }

  @PostMapping("/plans")
  @PreAuthorize("hasAuthority('SELLIN_CREATE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Plan r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "demand.create",
            r,
            () -> {
              catalog.npp(r.nppId());
              for (var l : r.lines()) catalog.toBase(l.skuId(), l.uom(), l.qty());
              var p = new DemandPlan();
              p.nppId = r.nppId();
              p.month = r.month().withDayOfMonth(1);
              p.status = "DRAFT";
              p.linesJson = json.write(r.lines());
              plans.saveAndFlush(p);
              return Map.of("id", p.id, "status", p.status);
            }));
  }

  @PostMapping("/plans/{id}/transition")
  @PreAuthorize("hasAuthority('SELLIN_CREATE') or hasAuthority('ORDER_APPROVE')")
  public ApiResponse<?> transition(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody State r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "demand.transition." + id,
            r,
            () -> {
              var p = plans.lockById(id).orElseThrow(BusinessException::missing);
              catalog.npp(p.nppId);
              boolean valid =
                  p.status.equals("DRAFT") && r.status().equals("SUBMITTED")
                      || p.status.equals("SUBMITTED")
                          && Set.of("ACCEPTED", "ADJUSTED").contains(r.status())
                          && actors.current().nsx();
              BusinessException.require(
                  valid, "Chuyển kế hoạch không hợp lệ hoặc thiếu quyền NSX.");
              p.status = r.status();
              p.note = r.reason();
              audit.add(
                  actors.current().id(), "DemandPlan", id, p.status, Map.of("reason", r.reason()));
              return Map.of("id", id, "status", p.status);
            }));
  }

  @GetMapping("/sell-out")
  public ApiResponse<?> sellout(
      @RequestParam Long nppId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
    BusinessException.require(
        !to.isBefore(from) && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 366,
        "Khoảng ngày tối đa 366 ngày.");
    return ApiResponse.ok(
        Map.of(
            "generatedAt",
            Instant.now(),
            "layer",
            "PROVISIONAL",
            "lines",
            sales.deliveredInPeriod(nppId, from, to)));
  }

  @GetMapping("/replenishment")
  public ApiResponse<?> suggestions(
      @RequestParam Long nppId,
      @RequestParam(defaultValue = "30") int lookbackDays,
      @RequestParam(defaultValue = "14") int coverDays) {
    BusinessException.require(
        lookbackDays >= 7 && lookbackDays <= 180 && coverDays >= 1 && coverDays <= 180,
        "Lịch sử 7–180 ngày; số ngày phủ 1–180.");
    catalog.npp(nppId);
    var sold = new HashMap<Long, BigDecimal>();
    for (var line :
        sales.deliveredInPeriod(
            nppId, Dates.today().minusDays(lookbackDays), Dates.today().minusDays(1)))
      sold.merge(
          ((Number) line.get("skuId")).longValue(), (BigDecimal) line.get("qty"), BigDecimal::add);
    var inbound = sellin.expectedInbound(nppId);
    Long warehouse = catalog.nppWarehouse(nppId);
    return ApiResponse.ok(
        catalog.products("").stream()
            .filter(CatalogFacade.Product::active)
            .map(
                p -> {
                  BigDecimal
                      rate =
                          sold.getOrDefault(p.id(), BigDecimal.ZERO)
                              .divide(BigDecimal.valueOf(lookbackDays), 6, RoundingMode.HALF_UP),
                      available = inventory.available(warehouse, p.id()),
                      expected = inbound.getOrDefault(p.id(), BigDecimal.ZERO),
                      need =
                          rate.multiply(BigDecimal.valueOf(coverDays))
                              .subtract(available)
                              .subtract(expected)
                              .max(BigDecimal.ZERO)
                              .setScale(3, RoundingMode.CEILING);
                  return Map.of(
                      "skuId",
                      p.id(),
                      "name",
                      p.name(),
                      "unit",
                      p.baseUnit(),
                      "dailyBase",
                      rate,
                      "availableBase",
                      available,
                      "inboundBase",
                      expected,
                      "suggestedBase",
                      need,
                      "basis",
                      "DELIVERED_HISTORY_NOT_AI");
                })
            .toList());
  }
}
