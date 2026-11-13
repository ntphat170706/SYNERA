package vn.synera.salesorder;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.PeriodGuard;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.inventory.InventoryFacade;
import vn.synera.notify.EventFacade;
import vn.synera.policy.PricingFacade;
import vn.synera.salesorder.domain.*;
import vn.synera.salesorder.repo.*;
import vn.synera.ticket.TicketFacade;

@Service
@Transactional
public class SalesFacade {
  private final SalesOrderRepository orders;
  private final SalesLineRepository lines;
  private final DailyReportRepository reports;
  private final CatalogFacade catalog;
  private final PricingFacade pricing;
  private final InventoryFacade stock;
  private final PeriodGuard guard;
  private final ActorFacade actors;
  private final EventFacade events;
  private final TicketFacade tickets;
  private final AuditService audit;
  private final Jsons json;
  private final SalesReplacementRepository replacements;
  private final SalesReplacementLineRepository replacementLines;

  public SalesFacade(
      SalesOrderRepository orders,
      SalesLineRepository lines,
      DailyReportRepository reports,
      CatalogFacade catalog,
      PricingFacade pricing,
      InventoryFacade stock,
      PeriodGuard guard,
      ActorFacade actors,
      EventFacade events,
      TicketFacade tickets,
      AuditService audit,
      Jsons json,
      SalesReplacementRepository replacements,
      SalesReplacementLineRepository replacementLines) {
    this.orders = orders;
    this.lines = lines;
    this.reports = reports;
    this.catalog = catalog;
    this.pricing = pricing;
    this.stock = stock;
    this.guard = guard;
    this.actors = actors;
    this.events = events;
    this.tickets = tickets;
    this.audit = audit;
    this.json = json;
    this.replacements = replacements;
    this.replacementLines = replacementLines;
  }

  public Optional<Map<String, Object>> existing(String key) {
    return orders.findByRepIdAndIdempotencyKey(actors.current().id(), key).map(this::dto);
  }

  public Object create(
      Long storeId,
      String clientId,
      String key,
      Instant clientTime,
      List<DocumentRequests.Line> request) {
    return createExpected(storeId, clientId, key, clientTime, request, null);
  }

  public Object createExpected(
      Long storeId,
      String clientId,
      String key,
      Instant clientTime,
      List<DocumentRequests.Line> request,
      String expectedQuote) {
    guard.assertOpen(Dates.today());
    var s = catalog.store(storeId);
    BusinessException.require(
        !clientTime.isAfter(Instant.now().plusSeconds(300)), "Thời điểm client ở tương lai.");
    var quote = pricing.quote(s.nppId(), s.id(), request, Dates.today(), true);
    if (expectedQuote != null && !json.hash(quote).equals(json.hash(json.read(expectedQuote))))
      throw BusinessException.conflict(
          "PRICE_CHANGED", "Giá hoặc ưu đãi đã thay đổi; cần phát hành và xác nhận lại báo giá.");
    Long warehouse = catalog.nppWarehouse(s.nppId());
    for (var p : quote.lines())
      stock.assertAvailable(warehouse, p.skuId(), p.qty().multiply(p.factor()));
    var o = new SalesOrder();
    o.nppId = s.nppId();
    o.storeId = storeId;
    o.repId = actors.current().sales() ? actors.current().id() : s.repId();
    o.clientId = clientId;
    o.idempotencyKey = key;
    o.clientCreatedAt = clientTime;
    o.code = "SA-" + UUID.randomUUID().toString().substring(0, 16);
    o.status = "SUBMITTED";
    o.warehouseId = warehouse;
    o.subtotal = quote.subtotal();
    o.discount = quote.discount();
    o.total = quote.total();
    orders.saveAndFlush(o);
    audit.add(
        actors.current().id(),
        "SalesOrder",
        o.id,
        "CREATE",
        Map.of("repId", o.repId, "storeId", storeId));
    pricing.recordBenefits("SELL_OUT", o.id, quote);
    for (var p : quote.lines()) {
      var l = new SalesLine();
      l.orderId = o.id;
      l.skuId = p.skuId();
      l.uom = p.uom();
      l.qtyOrdered = p.qty();
      l.price = p.price();
      l.discount = p.discount();
      l.baseFactor = p.factor();
      lines.save(l);
    }
    events.add(
        "OrderCreated",
        o.id,
        o.nppId,
        o.repId,
        Map.of("channel", "SELL_OUT", "storeId", o.storeId, "orderId", o.id, "code", o.code));
    return detail(o.id);
  }

  private SalesOrder locked(Long id) {
    var o = orders.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(o.nppId);
    actors.assertOwnRep(o.repId);
    return o;
  }

  public Object transition(
      Long id, String action, Map<Long, BigDecimal> quantities, String reason) {
    guard.assertOpen(Dates.today());
    var o = locked(id);
    String next =
        switch (action) {
          case "confirm" -> "NPP_CONFIRMED";
          case "ship" -> "PICKING";
          case "dispatch" -> "DELIVERING";
          case "receipt" -> "DELIVERED";
          case "cancel" -> "CANCELLED";
          default -> throw BusinessException.invalid("Thao tác không hợp lệ.");
        };
    boolean valid =
        switch (o.status) {
          case "SUBMITTED" -> Set.of("NPP_CONFIRMED", "CANCELLED").contains(next);
          case "NPP_CONFIRMED" -> Set.of("PICKING", "CANCELLED").contains(next);
          case "PICKING" -> next.equals("DELIVERING");
          case "DELIVERING" -> next.equals("DELIVERED");
          default -> false;
        };
    if (!valid)
      throw BusinessException.conflict("INVALID_STATE_TRANSITION", o.status + " -> " + next);
    var all = lines.findByOrderId(id);
    if (action.equals("confirm")) {
      BusinessException.require(
          quantities != null && quantities.size() == all.size(), "Xác nhận mọi dòng.");
      for (var l : all.stream().sorted(Comparator.comparing(x -> x.skuId)).toList()) {
        var qty = quantities.get(l.id);
        BusinessException.require(
            qty != null && qty.signum() > 0 && qty.compareTo(l.qtyOrdered) <= 0,
            "Số lượng xác nhận không hợp lệ.");
        catalog.toBase(l.skuId, l.uom, qty);
        l.qtyConfirmed = qty;
        stock.reserve("SELL_OUT", id, l.id, o.warehouseId, l.skuId, qty.multiply(l.baseFactor));
      }
    }
    if (action.equals("ship")) {
      stock.ship("SELL_OUT", id);
      all.forEach(l -> l.qtyShipped = l.qtyConfirmed);
    }
    if (action.equals("receipt")) {
      BusinessException.require(
          quantities != null && quantities.size() == all.size(), "Nhập thực nhận mọi dòng.");
      for (var l : all) {
        var qty = quantities.get(l.id);
        BusinessException.require(
            qty != null && qty.signum() >= 0 && qty.compareTo(l.qtyShipped) <= 0,
            "Thực giao không vượt thực xuất.");
        l.qtyReceived = qty;
        if (qty.compareTo(l.qtyShipped) < 0) {

          next = "DELIVERED_SHORT";
          tickets.salesShortage(
              o.nppId,
              o.storeId,
              id,
              l.id,
              "Giao thiếu " + o.code,
              reason == null || reason.isBlank()
                  ? "Thực xuất " + l.qtyShipped + ", thực nhận " + qty + " (" + l.uom + ")."
                  : reason);
          events.add(
              "DeliveryShort", id, o.nppId, o.repId, Map.of("lineId", l.id, "storeId", o.storeId));
        }
      }
      o.deliveredAt = Instant.now();
    }
    if (action.equals("cancel")) {

      stock.release("SELL_OUT", id);
      pricing.releaseBenefits("SELL_OUT", id);
    }
    o.status = next;
    events.add(
        "SalesOrderUpdated",
        id,
        o.nppId,
        o.repId,
        Map.of(
            "channel",
            "SELL_OUT",
            "status",
            next,
            "storeId",
            o.storeId,
            "orderId",
            o.id,
            "code",
            o.code));
    audit.add(
        actors.current().id(),
        "SalesOrder",
        id,
        next,
        Map.of("reason", Objects.toString(reason, "")));
    return detail(id);
  }

  public List<Map<String, Object>> list(Long storeId) {
    var ids = catalog.visibleNppIds();
    var actor = actors.current();
    return orders
        .findAll(
            (r, q, cb) -> {
              var scope = r.get("nppId").in(ids);
              if (actor.sales()) scope = cb.and(scope, cb.equal(r.get("repId"), actor.id()));
              if (storeId != null) scope = cb.and(scope, cb.equal(r.get("storeId"), storeId));
              return scope;
            })
        .stream()
        .map(this::dto)
        .toList();
  }

  public PageSlice<Map<String, Object>> storePage(Long storeId, int page, int size) {
    catalog.store(storeId);
    var actor = actors.current();
    return PageSlice.from(
        orders
            .findAll(
                (r, q, cb) -> {
                  var scope = cb.equal(r.get("storeId"), storeId);
                  return actor.sales()
                      ? cb.and(scope, cb.equal(r.get("repId"), actor.id()))
                      : scope;
                },
                PageSlice.request(page, size))
            .map(this::dto));
  }

  public Map<String, Object> detail(Long id) {
    var o = orders.findById(id).orElseThrow(BusinessException::missing);
    catalog.npp(o.nppId);
    actors.assertOwnRep(o.repId);
    var result = new LinkedHashMap<>(dto(o));
    result.put(
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
                        Map.entry("qtyReplacementReceived", replacementQty(o.id, l.id, true)),
                        Map.entry(
                            "qtyAvailableToReplace",
                            l.qtyShipped
                                .subtract(l.qtyReceived)
                                .subtract(replacementQty(o.id, l.id, false))
                                .max(BigDecimal.ZERO)),
                        Map.entry("price", l.price),
                        Map.entry("lineTotal", l.qtyOrdered.multiply(l.price).subtract(l.discount)),
                        Map.entry("discount", l.discount)))
            .toList());
    return result;
  }

  private Map<String, Object> dto(SalesOrder o) {
    var result =
        new LinkedHashMap<String, Object>(
            Map.of(
                "id",
                o.id,
                "code",
                o.code,
                "nppId",
                o.nppId,
                "storeId",
                o.storeId,
                "repId",
                o.repId,
                "clientId",
                o.clientId,
                "status",
                o.status,
                "total",
                o.total,
                "createdAt",
                o.createdAt));
    result.put(
        "hasOutstandingShortage",
        o.status.equals("DELIVERED_SHORT")
            && lines.findByOrderId(o.id).stream()
                .anyMatch(
                    l ->
                        l.qtyShipped
                                .subtract(l.qtyReceived)
                                .subtract(replacementQty(o.id, l.id, true))
                                .signum()
                            > 0));
    return result;
  }

  private BigDecimal replacementQty(Long orderId, Long lineId, boolean receivedOnly) {
    BigDecimal total = BigDecimal.ZERO;
    for (var note : replacements.findByOrderId(orderId)) {
      if (note.status.equals("CANCELLED") || (receivedOnly && !note.status.equals("RECEIVED")))
        continue;
      for (var line : replacementLines.findByReplacementId(note.id))
        if (line.orderLineId.equals(lineId))
          total = total.add(note.status.equals("RECEIVED") ? line.qtyReceived : line.qty);
    }
    return total;
  }

  public Object daily(LocalDate day, String note, boolean submit, int visits) {
    var actor = actors.current();
    var existing =
        reports
            .findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.equal(r.get("repId"), actor.id()),
                        cb.equal(r.get("nppId"), actor.nppId()),
                        cb.equal(r.get("day"), day)))
            .stream()
            .findFirst();
    var report = existing.orElseGet(DailyReport::new);
    BusinessException.require(!"SUBMITTED".equals(report.status), "Báo cáo đã nộp, không ghi đè.");
    var own =
        orders.findAll(
            (r, q, cb) ->
                cb.and(
                    cb.equal(r.get("repId"), actor.id()),
                    cb.equal(r.get("nppId"), actor.nppId()),
                    cb.greaterThanOrEqualTo(
                        r.get("createdAt"), day.atStartOfDay(Dates.VN).toInstant()),
                    cb.lessThan(
                        r.get("createdAt"), day.plusDays(1).atStartOfDay(Dates.VN).toInstant())));
    report.nppId = actor.nppId();
    report.repId = actor.id();
    report.day = day;
    report.orders = own.size();
    report.amount =
        own.stream()
            .filter(o -> !o.status.equals("CANCELLED"))
            .map(o -> o.total)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    report.visits = visits;
    report.note = note;
    report.status = submit ? "SUBMITTED" : "DRAFT";
    reports.saveAndFlush(report);
    return Map.of(
        "id",
        report.id,
        "status",
        report.status,
        "day",
        day,
        "orders",
        report.orders,
        "visits",
        visits,
        "amount",
        report.amount,
        "note",
        Objects.toString(note, ""));
  }

  public List<Map<String, Object>> deliveredInPeriod(Long nppId, LocalDate start, LocalDate end) {
    catalog.npp(nppId);
    var result =
        new ArrayList<Map<String, Object>>(
            orders.findByNppId(nppId).stream()
                .filter(o -> !actors.current().sales() || o.repId.equals(actors.current().id()))
                .filter(
                    o ->
                        Set.of("DELIVERED", "DELIVERED_SHORT").contains(o.status)
                            && o.deliveredAt != null
                            && !o.deliveredAt.atZone(Dates.VN).toLocalDate().isBefore(start)
                            && !o.deliveredAt.atZone(Dates.VN).toLocalDate().isAfter(end))
                .flatMap(
                    o ->
                        lines.findByOrderId(o.id).stream()
                            .map(
                                l ->
                                    Map.<String, Object>of(
                                        "skuId",
                                        l.skuId,
                                        "qty",
                                        l.qtyReceived.multiply(l.baseFactor),
                                        "amount",
                                        l.qtyOrdered
                                            .multiply(l.price)
                                            .subtract(l.discount)
                                            .multiply(l.qtyReceived)
                                            .divide(l.qtyOrdered, 2, RoundingMode.HALF_UP))))
                .toList());
    for (var replacement : replacements.findByNppId(nppId)) {
      if (!replacement.status.equals("RECEIVED")
          || replacement.receivedAt == null
          || (actors.current().sales() && !replacement.repId.equals(actors.current().id())))
        continue;
      var day = replacement.receivedAt.atZone(Dates.VN).toLocalDate();
      if (day.isBefore(start) || day.isAfter(end)) continue;
      for (var row : replacementLines.findByReplacementId(replacement.id)) {
        var original = lines.findById(row.orderLineId).orElseThrow(BusinessException::missing);
        result.add(
            Map.of(
                "skuId",
                row.skuId,
                "qty",
                row.qtyReceived.multiply(row.baseFactor),
                "amount",
                original
                    .qtyOrdered
                    .multiply(original.price)
                    .subtract(original.discount)
                    .multiply(row.qtyReceived)
                    .divide(original.qtyOrdered, 2, RoundingMode.HALF_UP)));
      }
    }
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> personalSummary(LocalDate from, LocalDate to) {
    var actor = actors.current();
    if (!actor.sales())
      throw new BusinessException("FORBIDDEN_SCOPE", "Chỉ xem số liệu Sales của chính mình.", 403);
    BusinessException.require(
        !to.isBefore(from) && java.time.temporal.ChronoUnit.DAYS.between(from, to) <= 366,
        "Khoảng ngày tối đa 366 ngày.");
    catalog.npp(actor.nppId());
    var own =
        orders
            .findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.equal(r.get("repId"), actor.id()),
                        cb.equal(r.get("nppId"), actor.nppId())))
            .stream()
            .filter(o -> Objects.equals(o.nppId, actor.nppId()))
            .filter(
                o ->
                    !o.createdAt.atZone(Dates.VN).toLocalDate().isBefore(from)
                        && !o.createdAt.atZone(Dates.VN).toLocalDate().isAfter(to))
            .filter(o -> !"CANCELLED".equals(o.status))
            .toList();
    var received =
        deliveredInPeriod(actor.nppId(), from, to).stream()
            .map(row -> (BigDecimal) row.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Map.of(
        "from",
        from,
        "to",
        to,
        "layer",
        "PROVISIONAL",
        "generatedAt",
        Instant.now(),
        "orderCount",
        own.size(),
        "orderValue",
        own.stream().map(o -> o.total).reduce(BigDecimal.ZERO, BigDecimal::add),
        "receivedSales",
        received,
        "repId",
        actor.id());
  }
}
