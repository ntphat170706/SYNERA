package vn.synera.delivery;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.PeriodGuard;
import vn.synera.common.*;
import vn.synera.credit.CreditFacade;
import vn.synera.delivery.domain.*;
import vn.synera.delivery.repo.*;
import vn.synera.iam.ActorFacade;
import vn.synera.inventory.InventoryFacade;
import vn.synera.notify.EventFacade;
import vn.synera.sellin.SellInFacade;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ReturnFacade {
  private final ReturnNoteRepository notes;
  private final ReturnLineRepository lines;
  private final SellInFacade orders;
  private final CatalogFacade catalog;
  private final CreditFacade credit;
  private final InventoryFacade stock;
  private final PeriodGuard guard;
  private final ActorFacade actors;
  private final AuditService audit;
  private final EventFacade events;

  public ReturnFacade(
      ReturnNoteRepository notes,
      ReturnLineRepository lines,
      SellInFacade orders,
      CatalogFacade catalog,
      CreditFacade credit,
      InventoryFacade stock,
      PeriodGuard guard,
      ActorFacade actors,
      AuditService audit,
      EventFacade events) {
    this.notes = notes;
    this.lines = lines;
    this.orders = orders;
    this.catalog = catalog;
    this.credit = credit;
    this.stock = stock;
    this.guard = guard;
    this.actors = actors;
    this.audit = audit;
    this.events = events;
  }

  public Object create(Long orderId, Map<Long, BigDecimal> quantities, String reason) {
    var order = orders.lockForReturn(orderId);
    BusinessException.require(
        Set.of("RECEIVED", "RECONCILED", "CLOSED", "AUTO_RECEIVED").contains(order.get("status")),
        "Chỉ hoàn hàng đã nhận/đối soát.");
    BusinessException.require(
        quantities != null && !quantities.isEmpty(), "Cần lý do và dòng hoàn.");
    var note = new ReturnNote();
    note.orderId = orderId;
    note.nppId = ((Number) order.get("nppId")).longValue();
    note.reason = reason;
    note.status = "DRAFT";
    notes.saveAndFlush(note);
    var source = (List<Map<String, Object>>) order.get("lines");
    BigDecimal amount = BigDecimal.ZERO;
    for (var e : quantities.entrySet()) {
      var original =
          source.stream()
              .filter(l -> ((Number) l.get("id")).longValue() == e.getKey())
              .findFirst()
              .orElseThrow(BusinessException::missing);
      BigDecimal received = (BigDecimal) original.get("qtyReceived"),
          ordered = (BigDecimal) original.get("qtyOrdered");
      BusinessException.require(
          e.getValue() != null
              && e.getValue().signum() > 0
              && e.getValue().stripTrailingZeros().scale() <= 3
              && e.getValue().compareTo(received) <= 0,
          "Số hoàn vượt số nhận tốt.");
      var line = new ReturnLine();
      line.returnId = note.id;
      line.orderLineId = e.getKey();
      line.skuId = ((Number) original.get("skuId")).longValue();
      line.qty = e.getValue();
      line.baseFactor = (BigDecimal) original.get("baseFactor");
      line.amount =
          e.getValue()
              .multiply((BigDecimal) original.get("price"))
              .subtract(
                  ((BigDecimal) original.get("discount"))
                      .multiply(e.getValue())
                      .divide(ordered, 2, RoundingMode.HALF_UP))
              .setScale(2, RoundingMode.HALF_UP);
      lines.save(line);
      amount = amount.add(line.amount);
    }
    note.amount = amount;
    return Map.of("id", note.id, "status", note.status, "amount", amount);
  }

  public Object approve(Long id, String reason) {
    var note = notes.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(note.nppId);
    BusinessException.require(note.status.equals("DRAFT"), "Cần bản nháp và lý do duyệt.");
    var order = orders.lockForReturn(note.orderId);
    var original = (List<Map<String, Object>>) order.get("lines");
    for (var line : lines.findByReturnId(id)) {
      BigDecimal already =
          lines.findByOrderLineId(line.orderLineId).stream()
              .filter(
                  l ->
                      !l.returnId.equals(id)
                          && notes
                              .findById(l.returnId)
                              .map(n -> Set.of("APPROVED", "RECEIVED").contains(n.status))
                              .orElse(false))
              .map(l -> l.qty)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal received =
          (BigDecimal)
              original.stream()
                  .filter(l -> ((Number) l.get("id")).longValue() == line.orderLineId)
                  .findFirst()
                  .orElseThrow()
                  .get("qtyReceived");
      BusinessException.require(
          already.add(line.qty).compareTo(received) <= 0, "Các phiếu hoàn cộng dồn vượt số nhận.");
    }
    note.status = "APPROVED";
    audit.add(actors.current().id(), "Return", id, "APPROVE", Map.of("reason", reason));
    return Map.of("id", id, "status", note.status);
  }

  public Object receive(Long id) {
    guard.assertOpen(Dates.today());
    var note = notes.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(note.nppId);
    BusinessException.require(
        note.status.equals("APPROVED") && actors.current().nsx(),
        "Chỉ NSX ghi nhận hoàn đã được duyệt.");
    credit.lockExposure(note.nppId);
    for (var line : lines.findByReturnId(id))
      stock.returnFromReceipt(
          catalog.nppWarehouse(note.nppId),
          catalog.quarantineWarehouse(),
          note.orderId,
          line.orderLineId,
          line.skuId,
          line.qty.multiply(line.baseFactor),
          id);
    credit.refund(note.nppId, id, note.orderId, note.amount);
    note.status = "RECEIVED";
    events.add(
        "ReturnPosted", id, note.nppId, actors.current().id(), Map.of("amount", note.amount));
    audit.add(
        actors.current().id(),
        "Return",
        id,
        "POST",
        Map.of("amount", note.amount, "destination", "QUARANTINE"));
    return Map.of("id", id, "status", note.status, "amount", note.amount);
  }

  public List<?> list() {
    var ids = catalog.visibleNppIds();
    return notes.findAll((r, q, cb) -> r.get("nppId").in(ids)).stream()
        .map(
            n ->
                Map.of(
                    "id", n.id, "nppId", n.nppId, "orderId", n.orderId, "status", n.status,
                    "amount", n.amount, "reason", n.reason))
        .toList();
  }

  public Map<Long, BigDecimal> refunds(LocalDate start, LocalDate end) {
    var result = new HashMap<Long, BigDecimal>();
    for (var n :
        notes.findAll(
            (r, q, cb) ->
                cb.and(
                    cb.equal(r.get("status"), "RECEIVED"),
                    cb.greaterThanOrEqualTo(
                        r.get("updatedAt"), start.atStartOfDay(Dates.VN).toInstant()),
                    cb.lessThan(
                        r.get("updatedAt"), end.plusDays(1).atStartOfDay(Dates.VN).toInstant()))))
      result.merge(n.nppId, n.amount, BigDecimal::add);
    return result;
  }
}
