package vn.synera.inventory;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.inventory.domain.*;
import vn.synera.inventory.repo.*;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class InventoryFacade {
  private final StockLotRepository lots;
  private final StockMovementRepository movements;
  private final AllocationRepository allocations;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;
  private final SqlRecords sql;
  private final jakarta.persistence.EntityManager entityManager;
  private final vn.synera.closing.PeriodGuard guard;

  public InventoryFacade(
      StockLotRepository lots,
      StockMovementRepository movements,
      AllocationRepository allocations,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit,
      SqlRecords sql,
      jakarta.persistence.EntityManager entityManager,
      vn.synera.closing.PeriodGuard guard) {
    this.lots = lots;
    this.movements = movements;
    this.allocations = allocations;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
    this.sql = sql;
    this.entityManager = entityManager;
    this.guard = guard;
  }

  private StockLot lockLot(Long id) {
    var lot = lots.lockById(id).orElseThrow();
    entityManager.flush();
    entityManager.refresh(lot, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    return lot;
  }

  public BigDecimal available(Long warehouse, Long sku) {
    return matching(warehouse, sku).stream()
        .filter(x -> x.expiryDate == null || !x.expiryDate.isBefore(vn.synera.common.Dates.today()))
        .map(x -> x.qtyOnHand.subtract(x.qtyReserved))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<StockLot> matching(Long warehouse, Long sku) {
    return lots
        .findAll(
            (r, q, cb) ->
                cb.and(cb.equal(r.get("warehouseId"), warehouse), cb.equal(r.get("skuId"), sku)))
        .stream()
        .sorted(
            Comparator.comparing(
                    (StockLot l) -> l.expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(l -> l.id))
        .toList();
  }

  public void assertAvailable(Long warehouse, Long sku, BigDecimal qty) {
    if (available(warehouse, sku).compareTo(qty) < 0)
      throw BusinessException.conflict("STOCK_INSUFFICIENT", "Không đủ tồn khả dụng SKU " + sku);
  }

  public void reserve(
      String type, Long orderId, Long lineId, Long warehouse, Long sku, BigDecimal qty) {
    BigDecimal need = qty;
    for (var candidate : matching(warehouse, sku)) {
      var lot = lockLot(candidate.id);
      if (lot.expiryDate != null && lot.expiryDate.isBefore(vn.synera.common.Dates.today()))
        continue;
      var take = lot.qtyOnHand.subtract(lot.qtyReserved).min(need);
      if (take.signum() > 0) {
        lot.qtyReserved = lot.qtyReserved.add(take);
        var allocation = new Allocation();
        allocation.documentType = type;
        allocation.orderId = orderId;
        allocation.lineId = lineId;
        allocation.lotId = lot.id;
        allocation.qty = take;
        allocations.save(allocation);
        need = need.subtract(take);
      }
      if (need.signum() == 0) break;
    }
    if (need.signum() > 0)
      throw BusinessException.conflict("STOCK_INSUFFICIENT", "Không đủ tồn để giữ hàng.");
  }

  public void ship(String type, Long orderId) {
    var all =
        allocations.findByOrderId(orderId).stream()
            .filter(x -> x.documentType.equals(type))
            .sorted(Comparator.comparing(x -> x.lotId))
            .toList();
    BusinessException.require(!all.isEmpty(), "Chưa giữ tồn cho đơn.");
    for (var a : all) {
      BusinessException.require(!a.shipped, "Phân bổ đã xuất kho.");
      var lot = lockLot(a.lotId);
      lot.qtyReserved = lot.qtyReserved.subtract(a.qty);
      lot.qtyOnHand = lot.qtyOnHand.subtract(a.qty);
      a.shipped = true;
      movement(lot, "OUT", a.qty.negate(), type, orderId);
    }
  }

  public void release(String type, Long orderId) {
    for (var a :
        allocations.findByOrderId(orderId).stream()
            .filter(x -> x.documentType.equals(type) && !x.shipped)
            .sorted(Comparator.comparing(x -> x.lotId))
            .toList()) {
      var lot = lockLot(a.lotId);
      lot.qtyReserved = lot.qtyReserved.subtract(a.qty);
      allocations.delete(a);
    }
  }

  public void receive(
      Long warehouse, Long sku, String lotNo, BigDecimal qty, String refType, Long refId) {
    BusinessException.require(
        qty.signum() > 0 && qty.stripTrailingZeros().scale() <= 3, "Số nhập phải dương.");
    var existing = matching(warehouse, sku).stream().filter(x -> x.lotNo.equals(lotNo)).findFirst();
    StockLot lot;
    if (existing.isPresent()) lot = lockLot(existing.get().id);
    else {
      lot = new StockLot();
      lot.warehouseId = warehouse;
      lot.skuId = sku;
      lot.lotNo = lotNo;
      lot.expiryDate = vn.synera.common.Dates.today().plusMonths(12);
      lots.saveAndFlush(lot);
    }
    lot.qtyOnHand = lot.qtyOnHand.add(qty);
    movement(lot, "IN", qty, refType, refId);
  }

  private void movement(StockLot lot, String type, BigDecimal qty, String reference, Long id) {
    var m = new StockMovement();
    m.warehouseId = lot.warehouseId;
    m.skuId = lot.skuId;
    m.lotNo = lot.lotNo;
    m.movementType = type;
    m.qty = qty;
    m.referenceType = reference;
    m.referenceId = id;
    movements.save(m);
  }

  public List<Map<String, Object>> balances(Long warehouse) {
    catalog.warehouse(warehouse);
    return lots.findAll((r, q, cb) -> cb.equal(r.get("warehouseId"), warehouse)).stream()
        .map(
            l ->
                Map.<String, Object>of(
                    "id",
                    l.id,
                    "warehouseId",
                    l.warehouseId,
                    "skuId",
                    l.skuId,
                    "lotNo",
                    l.lotNo,
                    "expiryDate",
                    Objects.toString(l.expiryDate, ""),
                    "onHand",
                    l.qtyOnHand,
                    "reserved",
                    l.qtyReserved,
                    "available",
                    l.qtyOnHand.subtract(l.qtyReserved),
                    "unit",
                    "BASE"))
        .toList();
  }

  public Object receipt(
      Long warehouse, Long sku, String lot, BigDecimal qty, LocalDate expiry, String reason) {
    guard.assertOpen(Dates.today());
    catalog.lockWarehouses(List.of(warehouse));
    var destination = catalog.warehouse(warehouse);
    catalog.product(sku);

    BusinessException.require(
        expiry != null
            && (destination.type().equals("QUARANTINE") || !expiry.isBefore(Dates.today())),
        "Nhập hạn dùng thực; hàng hết hạn chỉ nhận vào kho cách ly.");
    var previous = matching(warehouse, sku).stream().filter(l -> l.lotNo.equals(lot)).findFirst();
    BusinessException.require(
        previous.isEmpty() || Objects.equals(previous.get().expiryDate, expiry),
        "Lô đã có hạn dùng khác. Tạo mã lô đúng để giữ truy vết.");
    receive(warehouse, sku, lot, qty, "MANUAL", actors.current().id());
    matching(warehouse, sku).stream()
            .filter(l -> l.lotNo.equals(lot))
            .findFirst()
            .orElseThrow()
            .expiryDate =
        expiry;
    audit.add(
        actors.current().id(),
        "Warehouse",
        warehouse,
        "RECEIPT",
        Map.of("sku", sku, "qty", qty, "reason", reason));
    return Map.of("received", true);
  }

  public void receiveAllocated(Long orderId, Long lineId, Long warehouse, BigDecimal quantity) {
    BigDecimal need = quantity;
    for (var a :
        allocations.findByOrderId(orderId).stream()
            .filter(x -> x.documentType.equals("SELL_IN") && x.lineId.equals(lineId) && x.shipped)
            .sorted(java.util.Comparator.comparing(x -> x.id))
            .toList()) {
      var source = lots.findById(a.lotId).orElseThrow();
      var take = a.qty.min(need);
      if (take.signum() > 0) {
        String lotNo = "SI-" + orderId + "-" + lineId + "-" + source.lotNo;
        receive(warehouse, source.skuId, lotNo, take, "SELL_IN_RECEIPT", orderId);
        var target =
            matching(warehouse, source.skuId).stream()
                .filter(l -> l.lotNo.equals(lotNo))
                .findFirst()
                .orElseThrow();
        target.expiryDate = source.expiryDate;
        need = need.subtract(take);
      }
      if (need.signum() == 0) break;
    }
    BusinessException.require(need.signum() == 0, "Không đủ phân bổ xuất kho để ghi nhận.");
  }

  public void returnFromReceipt(
      Long sourceWarehouse,
      Long quarantine,
      Long orderId,
      Long lineId,
      Long sku,
      BigDecimal qty,
      Long returnId) {
    BigDecimal need = qty;
    for (var candidate :
        matching(sourceWarehouse, sku).stream()
            .filter(l -> l.lotNo.startsWith("SI-" + orderId + "-" + lineId + "-"))
            .toList()) {
      var lot = lockLot(candidate.id);
      var take = lot.qtyOnHand.subtract(lot.qtyReserved).min(need);
      if (take.signum() > 0) {
        lot.qtyOnHand = lot.qtyOnHand.subtract(take);
        movement(lot, "RETURN_OUT", take.negate(), "RETURN", returnId);
        String targetNo = "RETURN-" + returnId + "-" + lot.id;
        receive(quarantine, sku, targetNo, take, "RETURN", returnId);
        var destination =
            matching(quarantine, sku).stream()
                .filter(x -> x.lotNo.equals(targetNo))
                .findFirst()
                .orElseThrow();
        destination.expiryDate = lot.expiryDate;
        need = need.subtract(take);
      }
      if (need.signum() == 0) break;
    }
    if (need.signum() > 0)
      throw BusinessException.conflict(
          "STOCK_INSUFFICIENT", "Lô hàng đã bán hoặc đang giữ, không đủ để hoàn.");
  }

  public Object transfer(Long source, Long destination, Long sku, BigDecimal qty, String reason) {
    guard.assertOpen(Dates.today());
    catalog.lockWarehouses(List.of(source, destination));
    var origin = catalog.warehouse(source);
    var target = catalog.warehouse(destination);
    catalog.product(sku);
    BusinessException.require(
        !source.equals(destination)
            && !origin.type().equals("QUARANTINE")
            && !target.type().equals("QUARANTINE")
            && qty != null
            && qty.signum() > 0
            && qty.stripTrailingZeros().scale() <= 3,
        "Kho nguồn/đích, số lượng hoặc lý do điều chuyển không hợp lệ.");
    long id =
        sql.insert(
            "stock_transfer",
            vn.synera.common.SqlRecords.versioned(
                "source_warehouse_id",
                source,
                "destination_warehouse_id",
                destination,
                "sku_id",
                sku,
                "qty_base",
                qty,
                "status",
                "POSTED",
                "reason",
                reason,
                "created_by",
                actors.current().id()));
    BigDecimal need = qty;
    for (var candidate : matching(source, sku)) {
      var lot = lockLot(candidate.id);
      if (lot.expiryDate != null && lot.expiryDate.isBefore(Dates.today())) continue;
      var take = lot.qtyOnHand.subtract(lot.qtyReserved).min(need);
      if (take.signum() <= 0) continue;
      lot.qtyOnHand = lot.qtyOnHand.subtract(take);
      movement(lot, "TRANSFER_OUT", take.negate(), "TRANSFER", id);
      String lotNo = "TR-" + id + "-" + lot.id;
      receive(destination, sku, lotNo, take, "TRANSFER", id);
      matching(destination, sku).stream()
              .filter(l -> l.lotNo.equals(lotNo))
              .findFirst()
              .orElseThrow()
              .expiryDate =
          lot.expiryDate;
      need = need.subtract(take);
      if (need.signum() == 0) break;
    }
    if (need.signum() > 0)
      throw BusinessException.conflict(
          "STOCK_INSUFFICIENT", "Không đủ tồn khả dụng để điều chuyển.");
    audit.add(
        actors.current().id(),
        "StockTransfer",
        id,
        "POST",
        Map.of(
            "source",
            source,
            "destination",
            destination,
            "sku",
            sku,
            "qtyBase",
            qty,
            "reason",
            reason));
    return Map.of("id", id, "status", "POSTED");
  }

  public List<Map<String, Object>> movementHistory(Long warehouse) {
    catalog.warehouse(warehouse);
    return movements.findAll((r, q, cb) -> cb.equal(r.get("warehouseId"), warehouse)).stream()
        .sorted(Comparator.comparing(m -> m.id, Comparator.reverseOrder()))
        .limit(2000)
        .map(
            m ->
                Map.<String, Object>of(
                    "id",
                    m.id,
                    "skuId",
                    m.skuId,
                    "lotNo",
                    m.lotNo,
                    "movementType",
                    m.movementType,
                    "qty",
                    m.qty,
                    "referenceType",
                    m.referenceType,
                    "referenceId",
                    m.referenceId,
                    "createdAt",
                    m.createdAt))
        .toList();
  }

  public void seed() {
    if (lots.count() > 0) return;
    for (var pair : Map.of(1L, 12000L, 2L, 2400L, 3L, 2400L).entrySet()) {
      for (long sku = 1; sku <= 6; sku++) {
        receive(pair.getKey(), sku, "OPENING", BigDecimal.valueOf(pair.getValue()), "OPENING", sku);
      }
    }
  }
}
