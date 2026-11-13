package vn.synera.salesorder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.PeriodGuard;
import vn.synera.common.*;
import vn.synera.crm.CrmFacade;
import vn.synera.iam.ActorFacade;
import vn.synera.inventory.InventoryFacade;
import vn.synera.notify.EventFacade;
import vn.synera.notify.TaskFacade;
import vn.synera.salesorder.domain.*;
import vn.synera.salesorder.repo.*;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ReplacementFacade {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
  private final SalesOrderRepository orders;
  private final SalesLineRepository sourceLines;
  private final SalesReplacementRepository notes;
  private final SalesReplacementLineRepository lines;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final InventoryFacade inventory;
  private final PeriodGuard periods;
  private final AuditService audit;
  private final EventFacade events;
  private final TaskFacade tasks;
  private final CrmFacade crm;

  public ReplacementFacade(
      SalesOrderRepository orders,
      SalesLineRepository sourceLines,
      SalesReplacementRepository notes,
      SalesReplacementLineRepository lines,
      CatalogFacade catalog,
      ActorFacade actors,
      InventoryFacade inventory,
      PeriodGuard periods,
      AuditService audit,
      EventFacade events,
      TaskFacade tasks,
      CrmFacade crm) {
    this.orders = orders;
    this.sourceLines = sourceLines;
    this.notes = notes;
    this.lines = lines;
    this.catalog = catalog;
    this.actors = actors;
    this.inventory = inventory;
    this.periods = periods;
    this.audit = audit;
    this.events = events;
    this.tasks = tasks;
    this.crm = crm;
  }

  private SalesOrder order(Long id, boolean lock) {
    var o =
        (lock ? orders.lockById(id) : orders.findById(id)).orElseThrow(BusinessException::missing);
    catalog.npp(o.nppId);
    actors.assertOwnRep(o.repId);
    return o;
  }

  private SalesReplacement note(Long id) {
    var n = notes.findById(id).orElseThrow(BusinessException::missing);
    order(n.orderId, true);
    // The original order serializes every replacement and its capacity calculation.
    entityManager.refresh(n, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    return n;
  }

  public List<Map<String, Object>> list(Long orderId) {
    if (orderId != null) order(orderId, false);
    var ids = catalog.visibleNppIds();
    var actor = actors.current();
    return notes
        .findAll(
            (r, q, cb) ->
                cb.and(
                    orderId == null ? cb.conjunction() : cb.equal(r.get("orderId"), orderId),
                    actor.sales()
                        ? cb.and(r.get("nppId").in(ids), cb.equal(r.get("repId"), actor.id()))
                        : r.get("nppId").in(ids)))
        .stream()
        .map(this::dto)
        .toList();
  }

  public Map<String, Object> detail(Long id) {
    var n = notes.findById(id).orElseThrow(BusinessException::missing);
    order(n.orderId, false);
    var result = dto(n);
    result.put("history", audit.history("SalesReplacement", id));
    return result;
  }

  public Map<String, Object> create(
      Long orderId, Map<Long, BigDecimal> quantities, Instant due, String reason) {
    periods.assertOpen(Dates.today());
    var o = order(orderId, true);
    BusinessException.require(
        o.status.equals("DELIVERED_SHORT"), "Chỉ đề xuất giao bù cho đơn đã ghi giao thiếu.");
    BusinessException.require(
        due != null && due.isAfter(Instant.now()), "Cần hạn xử lý trong tương lai.");
    BusinessException.require(
        quantities != null && !quantities.isEmpty(), "Cần lý do và dòng cần giao bù.");
    var n = new SalesReplacement();
    n.orderId = o.id;
    n.nppId = o.nppId;
    n.storeId = o.storeId;
    n.repId = o.repId;
    n.warehouseId = o.warehouseId;
    n.createdBy = actors.current().id();
    n.status = "PROPOSED";
    n.dueAt = due;
    n.reason = reason;
    notes.saveAndFlush(n);
    for (var entry : quantities.entrySet()) {
      var source =
          sourceLines.findByOrderId(o.id).stream()
              .filter(l -> l.id.equals(entry.getKey()))
              .findFirst()
              .orElseThrow(BusinessException::missing);
      var qty = entry.getValue();
      BusinessException.require(
          qty != null && qty.signum() > 0 && qty.stripTrailingZeros().scale() <= 3,
          "Lượng giao bù phải dương, tối đa 3 số lẻ.");
      catalog.toBase(source.skuId, source.uom, qty);
      var available =
          source.qtyShipped.subtract(source.qtyReceived).subtract(claimed(o.id, source.id));
      BusinessException.require(
          qty.compareTo(available) <= 0, "Lượng giao bù vượt phần thiếu chưa được xử lý.");
      var l = new SalesReplacementLine();
      l.replacementId = n.id;
      l.orderLineId = source.id;
      l.skuId = source.skuId;
      l.uom = source.uom;
      l.baseFactor = source.baseFactor;
      l.qty = qty;
      lines.saveAndFlush(l);
    }
    tasks.create(
        o.nppId,
        o.storeId,
        o.repId,
        "Báo lại khách về phần thiếu của đơn " + o.code,
        due,
        "SALES_REPLACEMENT",
        n.id);
    event(n, "ReplacementProposed", reason);
    return dto(n);
  }

  private BigDecimal claimed(Long orderId, Long lineId) {
    BigDecimal sum = BigDecimal.ZERO;
    for (var n : notes.findByOrderId(orderId))
      if (!n.status.equals("CANCELLED"))
        for (var l : lines.findByReplacementId(n.id))
          if (l.orderLineId.equals(lineId))
            sum = sum.add(n.status.equals("RECEIVED") ? l.qtyReceived : l.qty);
    return sum;
  }

  public Map<String, Object> transition(
      Long id, String action, Map<Long, BigDecimal> received, String reason) {
    periods.assertOpen(Dates.today());
    var n = note(id);

    var all =
        lines.findByReplacementId(n.id).stream()
            .sorted(Comparator.comparing(l -> l.skuId))
            .toList();
    switch (action) {
      case "approve" -> {
        actors.require("SALES_APPROVE");
        requireStatus(n, "PROPOSED");
        BusinessException.require(
            !n.createdBy.equals(actors.current().id()), "Người duyệt phải khác người đề xuất.");
        for (var l : all)
          inventory.reserve(
              "SALES_REPLACEMENT",
              n.id,
              l.id,
              n.warehouseId,
              l.skuId,
              l.qty.multiply(l.baseFactor));
        n.approvedBy = actors.current().id();
        n.status = "APPROVED";
      }
      case "ship" -> {
        actors.require("WAREHOUSE_WRITE");
        requireStatus(n, "APPROVED");
        inventory.ship("SALES_REPLACEMENT", n.id);
        n.status = "SHIPPED";
      }
      case "receipt" -> {
        actors.require("SALES_APPROVE");
        requireStatus(n, "SHIPPED");
        BusinessException.require(
            received != null && received.size() == all.size(),
            "Ghi thực nhận tất cả dòng giao bù.");
        for (var l : all) {
          var qty = received.get(l.id);
          BusinessException.require(
              qty != null
                  && qty.signum() >= 0
                  && qty.stripTrailingZeros().scale() <= 3
                  && qty.compareTo(l.qty) <= 0,
              "Thực nhận không vượt lượng giao bù.");
          l.qtyReceived = qty;
        }
        n.receivedAt = Instant.now();
        n.receiptNote = reason;
        n.status = "RECEIVED";
      }
      case "cancel" -> {
        actors.require("SALES_APPROVE");
        BusinessException.require(
            Set.of("PROPOSED", "APPROVED").contains(n.status), "Không hủy chứng từ đã xuất.");
        inventory.release("SALES_REPLACEMENT", n.id);
        n.status = "CANCELLED";
      }
      default ->
          throw BusinessException.conflict(
              "INVALID_STATE_TRANSITION", "Thao tác không được hỗ trợ.");
    }
    if (n.notifiedAt != null) {
      n.notifiedAt = null;
      n.notifiedBy = null;
      n.customerFeedback = null;
      tasks.create(
          n.nppId,
          n.storeId,
          n.repId,
          "Báo lại khách về thay đổi giao bù #" + n.id,
          n.dueAt,
          "SALES_REPLACEMENT",
          n.id);
    }
    event(n, "ReplacementUpdated", reason);
    reconcileFollowUp(n.orderId);
    return dto(n);
  }

  public Map<String, Object> feedback(Long id, String note, String expectedStatus) {
    var n = note(id);
    requireStatus(n, expectedStatus);
    var actor = actors.current();
    BusinessException.require(note != null && !note.isBlank(), "Cần nội dung đã báo lại khách.");
    if (!actor.id().equals(n.repId) && !actor.nsx())
      throw new BusinessException(
          "FORBIDDEN_SCOPE", "Chỉ Sales phụ trách hoặc NSX ghi kết quả liên hệ.", 403);
    BusinessException.require(
        n.notifiedAt == null, "Đã có kết quả phản hồi; không ghi đè lịch sử.");
    n.notifiedAt = Instant.now();
    n.notifiedBy = actor.id();
    n.customerFeedback = note;
    crm.interaction(n.storeId, "FOLLOW_UP", note);
    tasks.completeReference("SALES_REPLACEMENT", n.id, n.nppId, n.repId);
    event(n, "ReplacementCustomerUpdated", note);
    reconcileFollowUp(n.orderId);
    return dto(n);
  }

  private void requireStatus(SalesReplacement n, String status) {
    if (!n.status.equals(status))
      throw BusinessException.conflict(
          "INVALID_STATE_TRANSITION", "Trạng thái đã thay đổi. Tải lại chứng từ.");
  }

  /** Internal worker/command hook: serialize with the source order, preserve receipts and money. */
  public void reconcileFollowUp(Long orderId) {
    var source = orders.lockById(orderId).orElse(null);
    if (source == null || !source.status.equals("DELIVERED_SHORT")) return;
    var all = notes.findByOrderId(orderId);
    if (all.isEmpty() || all.stream().anyMatch(n -> n.notifiedAt == null)) return;
    for (var line : sourceLines.findByOrderId(orderId)) {
      BigDecimal received = BigDecimal.ZERO;
      for (var note : all)
        if (note.status.equals("RECEIVED"))
          for (var replacement : lines.findByReplacementId(note.id))
            if (replacement.orderLineId.equals(line.id))
              received = received.add(replacement.qtyReceived);
      if (line.qtyShipped.subtract(line.qtyReceived).subtract(received).signum() > 0) return;
    }
    tasks.resolveSalesDeliveryShort(orderId, source.nppId);
  }

  private void event(SalesReplacement n, String type, String note) {
    audit.add(
        actors.current().id(),
        "SalesReplacement",
        n.id,
        type,
        Map.of("orderId", n.orderId, "note", note, "status", n.status));
    events.add(
        type,
        n.id,
        n.nppId,
        n.repId,
        Map.of(
            "orderId", n.orderId, "storeId", n.storeId, "status", n.status, "replacementId", n.id));
  }

  private Map<String, Object> dto(SalesReplacement n) {
    var result = new LinkedHashMap<String, Object>();
    result.put("id", n.id);
    result.put("orderId", n.orderId);
    result.put("nppId", n.nppId);
    result.put("storeId", n.storeId);
    result.put("repId", n.repId);
    result.put("warehouseId", n.warehouseId);
    result.put("status", n.status);
    result.put("createdBy", n.createdBy);
    result.put("approvedBy", n.approvedBy);
    result.put("dueAt", n.dueAt);
    result.put("reason", n.reason);
    result.put("receivedAt", n.receivedAt);
    result.put("notifiedAt", n.notifiedAt);
    result.put("customerFeedback", n.customerFeedback);
    result.put("additionalCharge", BigDecimal.ZERO);
    result.put(
        "lines",
        lines.findByReplacementId(n.id).stream()
            .map(
                l ->
                    Map.of(
                        "id",
                        l.id,
                        "orderLineId",
                        l.orderLineId,
                        "skuId",
                        l.skuId,
                        "uom",
                        l.uom,
                        "qty",
                        l.qty,
                        "qtyReceived",
                        l.qtyReceived))
            .toList());
    return result;
  }
}
