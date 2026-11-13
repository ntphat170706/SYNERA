package vn.synera.trademkt.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.ticket.TicketFacade;
import vn.synera.trademkt.domain.*;
import vn.synera.trademkt.repo.*;

@RestController
@RequestMapping("/api/v1/trade")
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class TradeController {
  private final BudgetRepository budgets;
  private final TradeRequestRepository requests;
  private final MarketFeedbackRepository feedback;
  private final CatalogFacade catalog;
  private final TicketFacade tickets;
  private final ActorFacade actors;
  private final IdempotencyService idem;
  private final AuditService audit;
  private final vn.synera.notify.WorkflowFacade workflow;

  public TradeController(
      BudgetRepository budgets,
      TradeRequestRepository requests,
      MarketFeedbackRepository feedback,
      CatalogFacade catalog,
      TicketFacade tickets,
      ActorFacade actors,
      IdempotencyService idem,
      AuditService audit,
      vn.synera.notify.WorkflowFacade workflow) {
    this.budgets = budgets;
    this.requests = requests;
    this.feedback = feedback;
    this.catalog = catalog;
    this.tickets = tickets;
    this.actors = actors;
    this.idem = idem;
    this.audit = audit;
    this.workflow = workflow;
  }

  public record BudgetReq(
      @NotBlank String name,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotNull LocalDate from,
      @NotNull LocalDate to) {}

  public record Request(
      @NotNull Long nppId,
      @NotNull Long budgetId,
      @NotBlank String title,
      @NotNull @DecimalMin("0.01") BigDecimal amount) {}

  public record Decision(@NotNull @DecimalMin("0") BigDecimal approvedAmount, String reason) {
    public Decision {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Feedback(
      @NotNull Long nppId,
      @NotNull Long skuId,
      @NotBlank String topic,
      @NotBlank String description) {}

  @PostMapping("/budgets")
  @PreAuthorize("hasAuthority('TRADE_APPROVE')")
  public ApiResponse<?> budget(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody BudgetReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "budget.create",
            r,
            () -> {
              BusinessException.require(!r.to().isBefore(r.from()), "Ngày ngân sách không hợp lệ.");
              var b = new Budget();
              b.name = r.name();
              b.amount = r.amount();
              b.periodStart = r.from();
              b.periodEnd = r.to();
              budgets.saveAndFlush(b);
              return Map.of("id", b.id, "amount", b.amount);
            }));
  }

  @GetMapping("/requests")
  @PreAuthorize("hasAnyAuthority('SELLIN_CREATE','TRADE_APPROVE','CREDIT_WRITE')")
  public ApiResponse<?> list() {
    var ids = catalog.visibleNppIds();
    return ApiResponse.ok(
        requests.findAll((r, q, cb) -> r.get("nppId").in(ids)).stream()
            .map(
                r ->
                    Map.of(
                        "id",
                        r.id,
                        "nppId",
                        r.nppId,
                        "title",
                        r.title,
                        "requested",
                        r.requested,
                        "approved",
                        r.approved,
                        "status",
                        r.status))
            .toList());
  }

  @PostMapping("/requests")
  @PreAuthorize("hasAuthority('SELLIN_CREATE')")
  public ApiResponse<?> request(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "trade.create",
            r,
            () -> {
              catalog.npp(r.nppId());
              var b = budgets.findById(r.budgetId()).orElseThrow(BusinessException::missing);
              BusinessException.require(
                  !Dates.today().isBefore(b.periodStart) && !Dates.today().isAfter(b.periodEnd),
                  "Ngân sách chưa/không còn hiệu lực.");
              var t = new TradeRequest();
              t.nppId = r.nppId();
              t.budgetId = b.id;
              t.title = r.title();
              t.requested = r.amount();
              t.status = "SUBMITTED";
              requests.saveAndFlush(t);
              return Map.of("id", t.id, "status", t.status);
            }));
  }

  @PostMapping("/requests/{id}/decide")
  @PreAuthorize("hasAuthority('TRADE_APPROVE')")
  public ApiResponse<?> decide(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Decision r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "trade.decide." + id,
            r,
            () -> {
              var t = requests.lockById(id).orElseThrow(BusinessException::missing);
              catalog.npp(t.nppId);
              BusinessException.require(
                  Set.of("SUBMITTED", "PENDING_APPROVAL").contains(t.status)
                      && r.approvedAmount().compareTo(t.requested) <= 0,
                  "Đề xuất đã xử lý hoặc số duyệt vượt đề xuất.");
              if (t.status.equals("PENDING_APPROVAL"))
                BusinessException.require(
                    t.approved.compareTo(r.approvedAmount()) == 0,
                    "Không đổi số duyệt giữa các bước workflow.");
              if (r.approvedAmount().signum() > 0) {
                t.approved = r.approvedAmount();
                boolean complete =
                    workflow.approve("TRADE_REQUEST", id, t.nppId, r.approvedAmount(), r.reason());
                if (!complete) {
                  t.status = "PENDING_APPROVAL";
                  t.reason = r.reason();
                  return Map.of("id", id, "status", t.status);
                }
              }
              var b = budgets.lockById(t.budgetId).orElseThrow();
              BusinessException.require(
                  b.committed.add(r.approvedAmount()).compareTo(b.amount) <= 0,
                  "Vượt ngân sách khả dụng.");
              b.committed = b.committed.add(r.approvedAmount());
              t.approved = r.approvedAmount();
              t.reason = r.reason();
              t.status =
                  t.approved.signum() == 0
                      ? "REJECTED"
                      : t.approved.compareTo(t.requested) == 0 ? "APPROVED" : "PARTIALLY_APPROVED";
              audit.add(
                  actors.current().id(),
                  "TradeRequest",
                  id,
                  t.status,
                  Map.of("amount", t.approved, "reason", t.reason));
              return Map.of("id", id, "status", t.status);
            }));
  }

  @GetMapping("/feedback")
  public ApiResponse<?> feedback() {
    var ids = catalog.visibleNppIds();
    return ApiResponse.ok(
        feedback
            .findAll(
                (r, q, cb) ->
                    actors.current().sales()
                        ? cb.and(
                            r.get("nppId").in(ids),
                            cb.equal(r.get("createdBy"), actors.current().id()))
                        : r.get("nppId").in(ids))
            .stream()
            .map(
                f ->
                    Map.of(
                        "id",
                        f.id,
                        "skuId",
                        f.skuId,
                        "topic",
                        f.topic,
                        "description",
                        f.description,
                        "ticketId",
                        Objects.requireNonNullElse(f.ticketId, 0L)))
            .toList());
  }

  @PostMapping("/feedback")
  @PreAuthorize("hasAuthority('TICKET_WRITE')")
  public ApiResponse<?> feedback(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Feedback r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "feedback.create",
            r,
            () -> {
              catalog.npp(r.nppId());
              catalog.product(r.skuId());
              var f = new MarketFeedback();
              f.nppId = r.nppId();
              f.createdBy = actors.current().id();
              f.skuId = r.skuId();
              f.topic = r.topic();
              f.description = r.description();
              feedback.saveAndFlush(f);
              return Map.of("id", f.id);
            }));
  }

  @PostMapping("/feedback/{id}/ticket")
  @PreAuthorize("hasAuthority('TICKET_WRITE')")
  public ApiResponse<?> escalate(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "feedback.ticket." + id,
            Map.of(),
            () -> {
              var f = feedback.lockById(id).orElseThrow(BusinessException::missing);
              catalog.npp(f.nppId);
              if (actors.current().sales() && !Objects.equals(f.createdBy, actors.current().id()))
                throw BusinessException.missing();
              BusinessException.require(f.ticketId == null, "Phản hồi đã có ticket.");
              var t =
                  (Map<?, ?>)
                      tickets.create(
                          f.nppId,
                          null,
                          null,
                          null,
                          "Chất lượng: " + f.topic,
                          "QUALITY",
                          f.description);
              f.ticketId = ((Number) t.get("id")).longValue();
              return t;
            }));
  }
}
