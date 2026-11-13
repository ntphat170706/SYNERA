package vn.synera.closing;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.domain.*;
import vn.synera.closing.repo.*;
import vn.synera.common.*;
import vn.synera.credit.CreditFacade;
import vn.synera.iam.ActorFacade;
import vn.synera.sellin.SellInFacade;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ClosingService {
  private final ReportPeriodRepository periods;
  private final PeriodSnapshotRepository snapshots;
  private final AdjustmentRepository adjustments;
  private final SellInFacade orders;
  private final CreditFacade credit;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;
  private final PeriodGuard guard;
  private final vn.synera.notify.WorkflowFacade workflow;
  private final vn.synera.delivery.ReturnFacade returns;

  public ClosingService(
      ReportPeriodRepository periods,
      PeriodSnapshotRepository snapshots,
      AdjustmentRepository adjustments,
      SellInFacade orders,
      CreditFacade credit,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit,
      PeriodGuard guard,
      vn.synera.notify.WorkflowFacade workflow,
      vn.synera.delivery.ReturnFacade returns) {
    this.periods = periods;
    this.snapshots = snapshots;
    this.adjustments = adjustments;
    this.orders = orders;
    this.credit = credit;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
    this.guard = guard;
    this.workflow = workflow;
    this.returns = returns;
  }

  public Object create(String type, LocalDate start, LocalDate end) {
    guard.exclusive();
    BusinessException.require(
        Set.of("DAY", "WEEK", "MONTH", "QUARTER").contains(type) && !end.isBefore(start),
        "Kỳ không hợp lệ.");
    var existing =
        periods
            .findAll(
                (r, q, cb) ->
                    cb.and(cb.equal(r.get("type"), type), cb.equal(r.get("startDate"), start)))
            .stream()
            .findFirst();
    if (existing.isPresent()) {
      BusinessException.require(
          existing.get().endDate.equals(end), "Kỳ trùng ngày bắt đầu nhưng khác ngày kết thúc.");
      return dto(existing.get());
    }
    var p = new ReportPeriod();
    p.type = type;
    p.startDate = start;
    p.endDate = end;
    p.status = "OPEN";
    periods.saveAndFlush(p);
    return dto(p);
  }

  public Object adjustments() {
    var ids = catalog.visibleNppIds();
    return adjustments.findAll((r, q, cb) -> r.get("nppId").in(ids)).stream()
        .map(
            a ->
                SqlRecords.mutable(
                    "id",
                    a.id,
                    "nppId",
                    a.nppId,
                    "originalPeriodId",
                    a.originalPeriodId,
                    "postedPeriodId",
                    a.postedPeriodId,
                    "delta",
                    a.delta,
                    "status",
                    a.status,
                    "reason",
                    a.reason,
                    "approvedBy",
                    a.approvedBy))
        .toList();
  }

  public List<?> list() {
    return periods.findAll().stream().map(this::dto).toList();
  }

  public Object advance(Long id, String next, String reason) {
    guard.exclusive();
    BusinessException.require(
        actors.current().nsx()
            && (actors.current().region() == null || actors.current().region().isBlank()),
        "Chốt kỳ toàn hệ thống cần phạm vi toàn quốc.");
    var p = periods.lockById(id).orElseThrow(BusinessException::missing);
    var allowed =
        switch (p.status) {
          case "OPEN" -> Set.of("PRE_CLOSE");
          case "PRE_CLOSE" -> Set.of("CLOSED", "OPEN");
          case "CLOSED" -> Set.of("LOCKED");
          case "LOCKED" -> Set.of("PUBLISHED");
          default -> Set.<String>of();
        };
    if (!allowed.contains(next))
      throw BusinessException.conflict("INVALID_STATE_TRANSITION", p.status + " -> " + next);

    if (next.equals("CLOSED")) {
      BusinessException.require(
          !orders.unresolved(p.startDate, p.endDate), "Còn đơn nhận có chênh lệch chưa đối soát.");
      var amounts = orders.receivedTotals(p.startDate, p.endDate);
      for (var npp : catalog.npps()) {
        var s = new PeriodSnapshot();
        s.periodId = p.id;
        s.nppId = npp.id();
        s.amount =
            amounts
                .getOrDefault(npp.id(), BigDecimal.ZERO)
                .subtract(
                    returns
                        .refunds(p.startDate, p.endDate)
                        .getOrDefault(npp.id(), BigDecimal.ZERO));
        for (var adjustment :
            adjustments.findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.equal(r.get("postedPeriodId"), p.id),
                        cb.equal(r.get("nppId"), npp.id()),
                        cb.equal(r.get("status"), "APPROVED"))))
          s.amount = s.amount.add(adjustment.delta);
        s.debt = credit.account(npp.id()).balance();
        s.capturedAt = Instant.now();
        snapshots.save(s);
      }
      p.closedBy = actors.current().id();
    }
    if (next.equals("LOCKED")) p.lockedAt = Instant.now();
    p.status = next;
    audit.add(actors.current().id(), "ReportPeriod", id, next, Map.of("reason", reason));
    return dto(p);
  }

  public List<Map<String, Object>> snapshots(Long periodId) {
    var ids = catalog.visibleNppIds();
    return snapshots
        .findAll(
            (r, q, cb) -> cb.and(cb.equal(r.get("periodId"), periodId), r.get("nppId").in(ids)))
        .stream()
        .map(
            s ->
                Map.<String, Object>of(
                    "id",
                    s.id,
                    "periodId",
                    s.periodId,
                    "nppId",
                    s.nppId,
                    "amount",
                    s.amount,
                    "debt",
                    s.debt,
                    "capturedAt",
                    s.capturedAt,
                    "layer",
                    "CLOSED_SNAPSHOT"))
        .toList();
  }

  public Object adjustment(Long originalId, Long nppId, BigDecimal delta, String reason) {
    catalog.npp(nppId);
    var p = periods.findById(originalId).orElseThrow(BusinessException::missing);
    BusinessException.require(
        Set.of("CLOSED", "LOCKED", "PUBLISHED").contains(p.status),
        "Phiếu điều chỉnh chỉ dùng cho kỳ đã chốt.");
    BusinessException.require(delta.signum() != 0, "Cần giá trị khác 0 và lý do.");
    var a = new Adjustment();
    a.originalPeriodId = originalId;
    a.nppId = nppId;
    a.delta = delta;
    a.reason = reason;
    a.status = "DRAFT";
    adjustments.saveAndFlush(a);
    return Map.of("id", a.id, "status", a.status);
  }

  public Object approveAdjustment(Long id, Long currentPeriod) {
    guard.assertOpen(Dates.today());
    var a = adjustments.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(a.nppId);
    var p = periods.lockById(currentPeriod).orElseThrow(BusinessException::missing);
    BusinessException.require(
        Set.of("DRAFT", "PENDING_APPROVAL").contains(a.status)
            && p.status.equals("OPEN")
            && !Dates.today().isBefore(p.startDate)
            && !Dates.today().isAfter(p.endDate)
            && !currentPeriod.equals(a.originalPeriodId),
        "Cần kỳ mở hiện tại khác kỳ gốc.");
    BusinessException.require(a.createdAt != null, "Phiếu chưa được lưu.");
    boolean complete = workflow.approve("ADJUSTMENT", a.id, a.nppId, a.delta, a.reason);
    if (!complete) {
      a.status = "PENDING_APPROVAL";
      return Map.of("id", id, "status", a.status);
    }
    credit.adjust(a.nppId, a.id, a.delta);
    a.postedPeriodId = currentPeriod;
    a.approvedBy = actors.current().id();
    a.status = "APPROVED";
    audit.add(
        a.approvedBy,
        "Adjustment",
        id,
        "APPROVE",
        Map.of("delta", a.delta, "reason", a.reason, "postedPeriod", currentPeriod));
    return Map.of("id", id, "status", a.status, "postedPeriodId", currentPeriod);
  }

  public void ensureToday() {
    LocalDate date = Dates.today();
    create("DAY", date, date);
  }

  public Object closeSystem(String type, LocalDate start, LocalDate end) {
    var created = (Map<?, ?>) create(type, start, end);
    Long id = ((Number) created.get("id")).longValue();
    var p = periods.lockById(id).orElseThrow();
    if (p.status.equals("OPEN")) advance(id, "PRE_CLOSE", "Job chuẩn bị chốt");
    if (p.status.equals("PRE_CLOSE"))
      return advance(id, "CLOSED", "Job chốt tự động; kế toán khóa kỳ riêng");
    return dto(p);
  }

  private Map<String, Object> dto(ReportPeriod p) {
    return Map.of(
        "id",
        p.id,
        "type",
        p.type,
        "startDate",
        p.startDate,
        "endDate",
        p.endDate,
        "status",
        p.status,
        "lockedAt",
        Objects.toString(p.lockedAt, ""));
  }
}
