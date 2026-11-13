package vn.synera.sellin;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.PeriodGuard;
import vn.synera.common.*;
import vn.synera.credit.CreditFacade;
import vn.synera.iam.ActorFacade;
import vn.synera.inventory.InventoryFacade;
import vn.synera.notify.EventFacade;
import vn.synera.policy.PricingFacade;
import vn.synera.sellin.domain.*;
import vn.synera.sellin.repo.*;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class SellInFacade {
  private final SellInOrderRepository orders;
  private final SellInLineRepository lines;
  private final OrderHistoryRepository history;
  private final CatalogFacade catalog;
  private final PricingFacade pricing;
  private final CreditFacade credit;
  private final InventoryFacade stock;
  private final PeriodGuard guard;
  private final ActorFacade actors;
  private final AuditService audit;
  private final EventFacade events;
  private final vn.synera.ticket.TicketFacade tickets;
  private final vn.synera.closing.ReconciliationFacade reconciliation;
  private final vn.synera.iam.SystemParametersFacade settings;
  private final vn.synera.notify.WorkflowFacade workflow;

  public SellInFacade(
      SellInOrderRepository orders,
      SellInLineRepository lines,
      OrderHistoryRepository history,
      CatalogFacade catalog,
      PricingFacade pricing,
      CreditFacade credit,
      InventoryFacade stock,
      PeriodGuard guard,
      ActorFacade actors,
      AuditService audit,
      EventFacade events,
      vn.synera.ticket.TicketFacade tickets,
      vn.synera.closing.ReconciliationFacade reconciliation,
      vn.synera.iam.SystemParametersFacade settings,
      vn.synera.notify.WorkflowFacade workflow) {
    this.orders = orders;
    this.lines = lines;
    this.history = history;
    this.catalog = catalog;
    this.pricing = pricing;
    this.credit = credit;
    this.stock = stock;
    this.guard = guard;
    this.actors = actors;
    this.audit = audit;
    this.events = events;
    this.tickets = tickets;
    this.reconciliation = reconciliation;
    this.settings = settings;
    this.workflow = workflow;
  }

  private SellInOrder locked(Long id) {
    var o = orders.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(o.nppId);
    return o;
  }

  private void move(SellInOrder o, OrderStatus next, String note) {
    OrderStatus.valueOf(o.status).assertNext(next);
    o.status = next.name();
    var h = new OrderHistory();
    h.orderId = o.id;
    h.status = o.status;
    h.actorId = actors.current().id();
    h.note = note;
    h.occurredAt = Instant.now();
    history.save(h);
    audit.add(
        actors.current().id(),
        "SellInOrder",
        o.id,
        next.name(),
        Map.of("reason", Objects.toString(note, "")));
    events.add(
        "OrderStatusChanged", o.id, o.nppId, actors.current().id(), Map.of("status", o.status));
  }

  public Object create(DocumentRequests.Order req, String key) {
    guard.assertOpen(Dates.today());
    catalog.npp(req.nppId());
    var quote = pricing.quote(req.nppId(), req.lines(), Dates.today(), false);
    var o = new SellInOrder();
    o.nppId = req.nppId();
    o.code = "SI-" + UUID.randomUUID().toString().substring(0, 16);
    o.status = "DRAFT";
    o.warehouseId = catalog.manufacturerWarehouse();
    o.subtotal = quote.subtotal();
    o.discount = quote.discount();
    o.total = quote.total();
    o.idempotencyKey = key;
    orders.saveAndFlush(o);
    saveLines(o.id, quote);
    events.add("OrderCreated", o.id, o.nppId, actors.current().id(), Map.of("channel", "SELL_IN"));
    return detail(o.id);
  }

  private void saveLines(Long id, PricingFacade.Quote quote) {
    for (var p : quote.lines()) {
      var l = new SellInLine();
      l.orderId = id;
      l.skuId = p.skuId();
      l.uom = p.uom();
      l.qtyOrdered = p.qty();
      l.price = p.price();
      l.discount = p.discount();
      l.baseFactor = p.factor();
      lines.save(l);
    }
  }

  public BigDecimal inflight(Long nppId, Long excluded) {
    return orders.findByNppId(nppId).stream()
        .filter(
            o -> OrderStatus.CREDIT_EXPOSURE.contains(o.status) && !Objects.equals(o.id, excluded))
        .map(o -> o.total)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public Map<Long, BigDecimal> expectedInbound(Long nppId) {
    catalog.npp(nppId);
    var result = new HashMap<Long, BigDecimal>();
    for (var o :
        orders.findAll(
            (r, q, cb) ->
                cb.and(
                    cb.equal(r.get("nppId"), nppId),
                    r.get("status")
                        .in(
                            Set.of(
                                "APPROVED",
                                "CONFIRMED_QTY",
                                "SHIPPED",
                                "IN_DELIVERY",
                                "DELIVERED")))))
      for (var line : lines.findByOrderId(o.id)) {
        var qty =
            line.qtyShipped.signum() > 0
                ? line.qtyShipped
                : line.qtyConfirmed.signum() > 0 ? line.qtyConfirmed : line.qtyOrdered;
        result.merge(line.skuId, qty.multiply(line.baseFactor), BigDecimal::add);
      }
    return result;
  }

  public Object submit(Long id) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    BusinessException.require(o.status.equals("DRAFT"), "Chỉ gửi bản nháp.");
    var request =
        lines.findByOrderId(id).stream()
            .map(l -> new DocumentRequests.Line(l.skuId, l.uom, l.qtyOrdered))
            .toList();
    credit.lockExposure(o.nppId);
    var check = credit.check(o.nppId, o.total, inflight(o.nppId, id));
    var quote = pricing.quote(o.nppId, request, Dates.today(), true);
    check = credit.check(o.nppId, quote.total(), inflight(o.nppId, id));
    for (var p : quote.lines())
      stock.assertAvailable(o.warehouseId, p.skuId(), p.qty().multiply(p.factor()));
    lines.removeByOrderId(id);
    saveLines(id, quote);
    o.subtotal = quote.subtotal();
    o.discount = quote.discount();
    o.total = quote.total();
    pricing.recordBenefits("SELL_IN", o.id, quote);
    o.submittedAt = Instant.now();
    move(o, OrderStatus.SUBMITTED, null);
    o.holdReason = check.hold() ? (check.overdue() ? "OVERDUE" : "CREDIT_LIMIT_EXCEEDED") : null;
    move(o, check.hold() ? OrderStatus.ON_HOLD_CREDIT : OrderStatus.PENDING_APPROVAL, o.holdReason);
    workflow.start("SELL_IN", id, o.nppId, o.total);
    return detail(id);
  }

  public Object decision(Long id, String action, String reason) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    OrderStatus next =
        switch (action) {
          case "approve" -> OrderStatus.APPROVED;
          case "reject" -> OrderStatus.REJECTED;
          case "need-info" -> OrderStatus.NEED_INFO;
          default -> throw BusinessException.invalid("Thao tác duyệt không hợp lệ.");
        };

    if (action.equals("approve")) {
      BusinessException.require(
          Set.of("PENDING_APPROVAL", "ON_HOLD_CREDIT").contains(o.status), "Đơn chưa chờ duyệt.");
      if (!workflow.approve(
          "SELL_IN",
          id,
          o.nppId,
          o.total,
          reason == null || reason.isBlank() ? "Phê duyệt theo phân quyền" : reason))
        return detail(id);
    } else if (action.equals("reject")) workflow.cancel("SELL_IN", id, reason);
    move(o, next, reason);
    return detail(id);
  }

  public Object resubmit(Long id, String note) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    BusinessException.require(note != null && !note.isBlank(), "Cần thông tin bổ sung.");
    move(o, OrderStatus.PENDING_APPROVAL, note);
    return detail(id);
  }

  public Object cancel(Long id, String reason) {
    guard.assertOpen(Dates.today());
    var o = locked(id);

    if (!actors.current().nsx()
        && !Set.of("DRAFT", "PENDING_APPROVAL", "ON_HOLD_CREDIT", "NEED_INFO").contains(o.status))
      throw new BusinessException("FORBIDDEN_SCOPE", "NPP chỉ hủy trước khi duyệt.", 403);
    stock.release("SELL_IN", id);
    pricing.releaseBenefits("SELL_IN", id);
    move(o, OrderStatus.CANCELLED, reason);
    return detail(id);
  }

  public Object confirm(Long id, Map<Long, BigDecimal> quantities) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    OrderStatus.valueOf(o.status).assertNext(OrderStatus.CONFIRMED_QTY);
    var all = lines.findByOrderId(id);
    BusinessException.require(
        quantities.size() == all.size(), "Phải xác nhận mọi dòng đúng một lần.");
    for (var l : all.stream().sorted(Comparator.comparing(x -> x.skuId)).toList()) {
      var q = quantities.get(l.id);
      BusinessException.require(
          q != null && q.signum() > 0 && q.compareTo(l.qtyOrdered) <= 0,
          "Số xác nhận phải dương và không vượt số đặt.");
      catalog.toBase(l.skuId, l.uom, q);
      l.qtyConfirmed = q;
      stock.reserve("SELL_IN", id, l.id, o.warehouseId, l.skuId, q.multiply(l.baseFactor));
    }
    move(o, OrderStatus.CONFIRMED_QTY, null);
    return detail(id);
  }

  public Object ship(Long id) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    OrderStatus.valueOf(o.status).assertNext(OrderStatus.SHIPPED);
    credit.check(o.nppId, BigDecimal.ZERO, inflight(o.nppId, null));
    stock.ship("SELL_IN", id);
    BigDecimal invoiced = BigDecimal.ZERO;
    for (var l : lines.findByOrderId(id)) {
      l.qtyShipped = l.qtyConfirmed;
      var ratio = l.qtyConfirmed.divide(l.qtyOrdered, 12, RoundingMode.HALF_UP);
      invoiced =
          invoiced.add(
              l.qtyConfirmed
                  .multiply(l.price)
                  .subtract(l.discount.multiply(ratio))
                  .setScale(2, RoundingMode.HALF_UP));
    }
    credit.invoice(o.nppId, id, invoiced);
    move(o, OrderStatus.SHIPPED, null);
    return detail(id);
  }

  public Object delivery(Long id, String action) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    move(o, action.equals("dispatch") ? OrderStatus.IN_DELIVERY : OrderStatus.DELIVERED, null);
    if (o.status.equals("DELIVERED")) o.deliveredAt = Instant.now();
    return detail(id);
  }

  public Object receive(Long id, DocumentRequests.Receipt req) {
    return receiveInternal(id, req, false);
  }

  private Object receiveInternal(Long id, DocumentRequests.Receipt req, boolean automatic) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    BusinessException.require(o.status.equals("DELIVERED"), "Chỉ nhận đơn đã giao.");
    if (!actors.current().nsx())
      BusinessException.require(
          Objects.equals(actors.current().nppId(), o.nppId), "Không đúng NPP nhận.");
    var all = lines.findByOrderId(id);
    var input = new HashMap<Long, DocumentRequests.ReceiptLine>();
    for (var r : req.lines())
      BusinessException.require(input.put(r.lineId(), r) == null, "Không lặp dòng thực nhận.");
    BusinessException.require(input.size() == all.size(), "Phải xác nhận mọi dòng.");
    boolean diff = false;
    for (var l : all) {
      var r = input.get(l.id);
      BusinessException.require(
          r != null
              && r.received().signum() >= 0
              && r.damaged().signum() >= 0
              && r.returned().signum() >= 0
              && r.received().add(r.damaged()).add(r.returned()).compareTo(l.qtyShipped) <= 0,
          "Tổng nhận tốt/hỏng/hoàn không được vượt thực xuất.");
      l.qtyReceived = r.received();
      l.qtyDamaged = r.damaged();
      l.qtyReturned = r.returned();
      if (r.received().signum() > 0)
        stock.receiveAllocated(
            id, l.id, catalog.nppWarehouse(o.nppId), r.received().multiply(l.baseFactor));
      if (r.received().compareTo(l.qtyShipped) != 0) {
        diff = true;
        String description =
            req.reason().isBlank()
                ? "Thực xuất "
                    + l.qtyShipped
                    + ", nhận tốt "
                    + r.received()
                    + ", hỏng "
                    + r.damaged()
                    + ", hoàn "
                    + r.returned()
                    + " ("
                    + l.uom
                    + ")."
                : req.reason();
        reconciliation.shortage(o.nppId, id, l.id, description);
        tickets.create(
            o.nppId,
            null,
            id,
            l.id,
            "Chênh lệch nhận hàng " + o.code,
            "DELIVERY_SHORT",
            description);
        events.add(
            "DeliveryShort",
            id,
            o.nppId,
            actors.current().id(),
            Map.of("lineId", l.id, "expected", l.qtyShipped, "received", r.received()));
      }
    }

    o.receivedAt = Instant.now();
    move(
        o,
        automatic
            ? OrderStatus.AUTO_RECEIVED
            : diff ? OrderStatus.RECEIVED_DISCREPANCY : OrderStatus.RECEIVED,
        req.reason());
    return detail(id);
  }

  public List<Map<String, Object>> list(String status) {
    var ids = catalog.visibleNppIds();
    return orders
        .findAll(
            (r, q, cb) ->
                status == null || status.isBlank()
                    ? r.get("nppId").in(ids)
                    : cb.and(r.get("nppId").in(ids), cb.equal(r.get("status"), status)))
        .stream()
        .sorted(Comparator.comparing((SellInOrder x) -> x.id).reversed())
        .map(this::dto)
        .toList();
  }

  public Map<String, Object> detail(Long id) {
    var o = orders.findById(id).orElseThrow(BusinessException::missing);
    catalog.npp(o.nppId);
    var m = new LinkedHashMap<>(dto(o));
    m.put("benefits", pricing.appliedBenefits("SELL_IN", id));
    m.put(
        "lines",
        lines.findByOrderId(id).stream()
            .map(
                l ->
                    Map.ofEntries(
                        Map.entry("id", l.id),
                        Map.entry("skuId", l.skuId),
                        Map.entry("uom", l.uom),
                        Map.entry("qtyOrdered", l.qtyOrdered),
                        Map.entry("qtyConfirmed", l.qtyConfirmed),
                        Map.entry("qtyShipped", l.qtyShipped),
                        Map.entry("qtyReceived", l.qtyReceived),
                        Map.entry("qtyDamaged", l.qtyDamaged),
                        Map.entry("qtyReturned", l.qtyReturned),
                        Map.entry("price", l.price),
                        Map.entry("discount", l.discount),
                        Map.entry("lineTotal", l.qtyOrdered.multiply(l.price).subtract(l.discount)),
                        Map.entry("baseFactor", l.baseFactor)))
            .toList());
    m.put(
        "history",
        history.findByOrderId(id).stream()
            .map(
                h ->
                    Map.of(
                        "status",
                        h.status,
                        "at",
                        h.occurredAt,
                        "actorId",
                        h.actorId,
                        "note",
                        Objects.toString(h.note, "")))
            .toList());
    return m;
  }

  private Map<String, Object> dto(SellInOrder o) {
    return Map.ofEntries(
        Map.entry("id", o.id),
        Map.entry("code", o.code),
        Map.entry("nppId", o.nppId),
        Map.entry("status", o.status),
        Map.entry("subtotal", o.subtotal),
        Map.entry("discount", o.discount),
        Map.entry("total", o.total),
        Map.entry("createdAt", o.createdAt),
        Map.entry("version", o.version),
        Map.entry("holdReason", Objects.toString(o.holdReason, "")));
  }

  public Object reorder(Long id, String key) {
    var o = locked(id);
    return create(
        new DocumentRequests.Order(
            o.nppId,
            lines.findByOrderId(id).stream()
                .map(l -> new DocumentRequests.Line(l.skuId, l.uom, l.qtyOrdered))
                .toList()),
        key);
  }

  public int autoReceipts() {
    int count = 0;
    for (var candidate :
        orders.findAll(
            (r, q, cb) ->
                cb.and(
                    cb.equal(r.get("status"), "DELIVERED"),
                    cb.lessThan(
                        r.get("deliveredAt"),
                        Instant.now()
                            .minusSeconds(
                                settings
                                        .decimal("receipt-remind-hours", new BigDecimal("48"))
                                        .longValueExact()
                                    * 3600))))) {
      var o = locked(candidate.id);
      if (settings.decimal("receipt-auto-enabled", BigDecimal.ZERO).signum() > 0
          && o.deliveredAt.isBefore(
              Instant.now()
                  .minusSeconds(
                      settings.decimal("receipt-auto-hours", new BigDecimal("96")).longValueExact()
                          * 3600))) {
        var input =
            lines.findByOrderId(o.id).stream()
                .map(
                    l ->
                        new DocumentRequests.ReceiptLine(
                            l.id, l.qtyShipped, BigDecimal.ZERO, BigDecimal.ZERO))
                .toList();
        receiveInternal(
            o.id,
            new DocumentRequests.Receipt(
                input,
                "Tự xác nhận theo thời hạn cấu hình; giữ nhãn AUTO_RECEIVED",
                java.util.List.of()),
            true);
        count++;
      } else if (o.receiptRemindedAt == null) {
        o.receiptRemindedAt = Instant.now();
        events.add("ReceiptReminder", o.id, o.nppId, actors.current().id(), Map.of("code", o.code));
      }
    }
    return count;
  }

  public int reconcileMatched(LocalDate day) {
    int count = 0;
    for (var o :
        orders.findAll(
            (r, q, cb) ->
                cb.and(
                    r.get("status").in(Set.of("RECEIVED", "AUTO_RECEIVED")),
                    cb.greaterThanOrEqualTo(
                        r.get("receivedAt"), day.atStartOfDay(Dates.VN).toInstant()),
                    cb.lessThan(
                        r.get("receivedAt"),
                        day.plusDays(1).atStartOfDay(Dates.VN).toInstant())))) {
      reconcile(o.id, "Đối soát đêm theo nhận thực tế");
      count++;
    }
    return count;
  }

  public Object close(Long id, String reason) {
    var order = locked(id);

    move(order, OrderStatus.CLOSED, reason);
    return detail(id);
  }

  public Object reconcile(Long id, String reason) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    BusinessException.require(
        !reconciliation.unresolved(id) && !tickets.unresolvedOrder(id),
        "Cần giải quyết ticket và ngoại lệ trước khi đối soát.");
    move(o, OrderStatus.RECONCILED, reason);
    return detail(id);
  }

  public void recheckHeld(Long nppId) {
    for (var candidate :
        orders.findByNppId(nppId).stream()
            .filter(o -> o.status.equals("ON_HOLD_CREDIT"))
            .sorted(Comparator.comparing(o -> o.id))
            .toList()) {
      var o = locked(candidate.id);
      if (!credit.check(nppId, o.total, inflight(nppId, o.id)).hold())
        move(o, OrderStatus.PENDING_APPROVAL, "Kiểm tra lại sau thanh toán");
    }
  }

  public Map<String, Object> lockForReturn(Long id) {
    locked(id);
    return detail(id);
  }

  public Map<Long, BigDecimal> receivedTotals(LocalDate from, LocalDate to) {
    var result = new HashMap<Long, BigDecimal>();
    for (var o :
        orders.findAll(
            (r, q, cb) ->
                cb.and(
                    cb.greaterThanOrEqualTo(
                        r.get("receivedAt"), from.atStartOfDay(Dates.VN).toInstant()),
                    cb.lessThan(
                        r.get("receivedAt"), to.plusDays(1).atStartOfDay(Dates.VN).toInstant()),
                    r.get("status")
                        .in(Set.of("RECEIVED", "AUTO_RECEIVED", "RECONCILED", "CLOSED"))))) {
      BigDecimal amount =
          lines.findByOrderId(o.id).stream()
              .map(
                  l ->
                      l.qtyReceived
                          .multiply(l.price)
                          .subtract(
                              l.qtyOrdered.signum() == 0
                                  ? BigDecimal.ZERO
                                  : l.discount
                                      .multiply(l.qtyReceived)
                                      .divide(l.qtyOrdered, 2, RoundingMode.HALF_UP)))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      result.merge(o.nppId, amount, BigDecimal::add);
    }
    return result;
  }

  public boolean unresolved(LocalDate from, LocalDate to) {
    return orders
            .findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.equal(r.get("status"), "RECEIVED_DISCREPANCY"),
                        cb.greaterThanOrEqualTo(
                            r.get("receivedAt"), from.atStartOfDay(Dates.VN).toInstant()),
                        cb.lessThan(
                            r.get("receivedAt"),
                            to.plusDays(1).atStartOfDay(Dates.VN).toInstant())))
            .size()
        > 0;
  }
}
