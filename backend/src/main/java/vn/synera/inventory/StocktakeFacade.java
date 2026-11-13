package vn.synera.inventory;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.PeriodGuard;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.inventory.domain.*;
import vn.synera.inventory.repo.*;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class StocktakeFacade {
  private final StocktakeRepository takes;
  private final StocktakeLineRepository lines;
  private final StockLotRepository lots;
  private final StockMovementRepository movements;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;
  private final PeriodGuard guard;

  public StocktakeFacade(
      StocktakeRepository takes,
      StocktakeLineRepository lines,
      StockLotRepository lots,
      StockMovementRepository movements,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit,
      PeriodGuard guard) {
    this.takes = takes;
    this.lines = lines;
    this.lots = lots;
    this.movements = movements;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
    this.guard = guard;
  }

  public List<Map<String, Object>> list(Long warehouse) {
    catalog.warehouse(warehouse);
    return takes.findAll((r, q, cb) -> cb.equal(r.get("warehouseId"), warehouse)).stream()
        .map(
            t ->
                Map.<String, Object>of(
                    "id",
                    t.id,
                    "warehouseId",
                    t.warehouseId,
                    "status",
                    t.status,
                    "reason",
                    t.reason,
                    "createdBy",
                    t.createdBy,
                    "createdAt",
                    t.createdAt))
        .toList();
  }

  public Object create(Long warehouse, Map<Long, BigDecimal> counted, String reason) {
    catalog.warehouse(warehouse);
    BusinessException.require(!counted.isEmpty(), "Cần dòng kiểm kê và lý do.");
    var take = new Stocktake();
    take.warehouseId = warehouse;
    take.createdBy = actors.current().id();
    take.status = "PENDING_APPROVAL";
    take.reason = reason;
    takes.saveAndFlush(take);
    for (var e : counted.entrySet()) {
      var lot = lots.findById(e.getKey()).orElseThrow(BusinessException::missing);
      BusinessException.require(
          lot.warehouseId.equals(warehouse)
              && e.getValue() != null
              && e.getValue().signum() >= 0
              && e.getValue().stripTrailingZeros().scale() <= 3,
          "Lô hoặc số kiểm kê không hợp lệ.");
      var l = new StocktakeLine();
      l.stocktakeId = take.id;
      l.lotId = lot.id;
      l.baselineVersion = lot.version;
      l.systemQty = lot.qtyOnHand;
      l.countedQty = e.getValue();
      lines.save(l);
    }
    return Map.of("id", take.id, "status", take.status);
  }

  public Object approve(Long id, String reason) {
    guard.assertOpen(Dates.today());
    var take = takes.lockById(id).orElseThrow(BusinessException::missing);
    catalog.warehouse(take.warehouseId);
    BusinessException.require(
        take.status.equals("PENDING_APPROVAL"), "Phiếu kiểm kê không hợp lệ.");
    BusinessException.require(
        actors.current().admin() || !take.createdBy.equals(actors.current().id()),
        "Người tạo và người duyệt kiểm kê phải khác nhau.");
    for (var l :
        lines.findByStocktakeId(id).stream().sorted(Comparator.comparing(x -> x.lotId)).toList()) {
      var lot = lots.lockById(l.lotId).orElseThrow();
      if (!lot.version.equals(l.baselineVersion))
        throw BusinessException.conflict(
            "OPTIMISTIC_LOCK", "Tồn đã thay đổi sau lúc kiểm kê; cần tạo lại phiếu.");
      BusinessException.require(
          l.countedQty.compareTo(lot.qtyReserved) >= 0,
          "Số đếm không được thấp hơn lượng đang giữ.");
      BigDecimal delta = l.countedQty.subtract(lot.qtyOnHand);
      lot.qtyOnHand = l.countedQty;
      if (delta.signum() != 0) {
        var movement = new StockMovement();
        movement.warehouseId = lot.warehouseId;
        movement.skuId = lot.skuId;
        movement.lotNo = lot.lotNo;
        movement.movementType = "ADJUST";
        movement.qty = delta;
        movement.referenceType = "STOCKTAKE";
        movement.referenceId = id;
        movements.save(movement);
      }
    }
    take.status = "APPROVED";
    audit.add(actors.current().id(), "Stocktake", id, "APPROVE", Map.of("reason", reason));
    return Map.of("id", id, "status", take.status);
  }
}
