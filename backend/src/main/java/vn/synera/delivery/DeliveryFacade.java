package vn.synera.delivery;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.integration.StorageFacade;
import vn.synera.salesorder.SalesFacade;
import vn.synera.sellin.SellInFacade;

@Service
@Transactional
public class DeliveryFacade {
  public record Stop(String channel, Long orderId) {}

  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final SellInFacade sellin;
  private final SalesFacade sales;
  private final AuditService audit;
  private final StorageFacade storage;

  public DeliveryFacade(
      JdbcTemplate jdbc,
      SqlRecords sql,
      CatalogFacade catalog,
      ActorFacade actors,
      SellInFacade sellin,
      SalesFacade sales,
      AuditService audit,
      StorageFacade storage) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.catalog = catalog;
    this.actors = actors;
    this.sellin = sellin;
    this.sales = sales;
    this.audit = audit;
    this.storage = storage;
  }

  public List<Map<String, Object>> list() {
    var actor = actors.current();
    var result = new ArrayList<Map<String, Object>>();
    for (Long npp : catalog.visibleNppIds())
      result.addAll(
          actor.role().equals("DRIVER")
              ? jdbc.queryForList(
                  "select * from delivery_trip where npp_id=? and driver_id=? order by id desc"
                      + " limit 500",
                  npp,
                  actor.id())
              : jdbc.queryForList(
                  "select * from delivery_trip where npp_id=? order by id desc limit 500", npp));
    return result;
  }

  private Map<String, Object> trip(Long id, boolean lock) {
    var t = sql.row("delivery_trip", id, lock);
    catalog.npp(SqlRecords.id(t, "npp_id"));
    if (actors.current().role().equals("DRIVER")
        && !actors.current().id().equals(SqlRecords.id(t, "driver_id")))
      throw BusinessException.missing();
    return t;
  }

  public Object detail(Long id) {
    var t = trip(id, false);
    var stops =
        jdbc.queryForList("select * from delivery_stop where trip_id=? order by sequence_no", id);
    for (var stop : stops) {
      var order = order((String) stop.get("channel"), SqlRecords.id(stop, "order_id"));
      // Driver sees delivery quantities and destination, never commercial prices or ledger.
      var items = new ArrayList<Map<String, Object>>();
      for (Object raw : (List<?>) order.get("lines")) {
        var line = (Map<?, ?>) raw;
        items.add(
            SqlRecords.mutable(
                "id",
                line.get("id"),
                "skuId",
                line.get("skuId"),
                "uom",
                line.get("uom"),
                "qtyShipped",
                line.get("qtyShipped")));
      }
      stop.put("lines", items);
      if (stop.get("channel").equals("SELL_OUT"))
        stop.put("destination", catalog.store(((Number) order.get("storeId")).longValue()));
      else stop.put("destination", catalog.npp(((Number) order.get("nppId")).longValue()));
    }
    t.put("stops", stops);
    return t;
  }

  private Map<String, Object> order(String channel, Long id) {
    BusinessException.require(
        Set.of("SELL_IN", "SELL_OUT").contains(channel), "Loại đơn không hợp lệ.");
    return channel.equals("SELL_IN") ? sellin.detail(id) : sales.detail(id);
  }

  public Object create(Long npp, Long driver, LocalDate date, List<Stop> stops) {
    actors.require("WAREHOUSE_WRITE");
    catalog.npp(npp);
    BusinessException.require(
        actors.staff(npp, "DRIVER").stream()
            .anyMatch(a -> a.id().equals(driver) && a.role().equals("DRIVER")),
        "Chỉ phân chuyến cho tài xế trong NPP.");
    BusinessException.require(
        date != null
            && !date.isBefore(Dates.today())
            && stops != null
            && !stops.isEmpty()
            && stops.size() <= 100,
        "Ngày hoặc danh sách giao không hợp lệ.");
    BusinessException.require(
        stops.stream().map(s -> s.channel() + ":" + s.orderId()).distinct().count() == stops.size(),
        "Trùng đơn trong chuyến.");
    long id =
        sql.insert(
            "delivery_trip",
            SqlRecords.versioned(
                "npp_id",
                npp,
                "driver_id",
                driver,
                "delivery_date",
                java.sql.Date.valueOf(date),
                "status",
                "PLANNED"));
    int sequence = 0;
    for (var stop : stops) {
      var o = order(stop.channel(), stop.orderId());
      BusinessException.require(
          Objects.equals(((Number) o.get("nppId")).longValue(), npp)
              && o.get("status").equals(stop.channel().equals("SELL_IN") ? "SHIPPED" : "PICKING"),
          "Đơn phải cùng NPP và đã xuất kho.");
      sql.insert(
          "delivery_stop",
          SqlRecords.versioned(
              "trip_id",
              id,
              "channel",
              stop.channel(),
              "order_id",
              stop.orderId(),
              "sequence_no",
              ++sequence,
              "status",
              "PLANNED"));
    }
    audit.add(
        actors.current().id(),
        "DeliveryTrip",
        id,
        "CREATE",
        Map.of("driverId", driver, "orders", stops));
    return detail(id);
  }

  public Object dispatch(Long id) {
    var t = trip(id, true);
    BusinessException.require(
        t.get("status").equals("PLANNED"), "Chuyến không ở trạng thái chờ xuất phát.");
    for (var s :
        jdbc.queryForList("select * from delivery_stop where trip_id=? order by sequence_no", id)) {
      if (s.get("channel").equals("SELL_IN"))
        sellin.delivery(SqlRecords.id(s, "order_id"), "dispatch");
      else
        sales.transition(
            SqlRecords.id(s, "order_id"), "dispatch", Map.of(), "Xuất phát chuyến " + id);
    }
    jdbc.update(
        "update delivery_stop set status='DELIVERING',version=version+1,updated_at=? where"
            + " trip_id=?",
        Timestamp.from(Instant.now()),
        id);
    jdbc.update(
        "update delivery_trip set status='DELIVERING',version=version+1,updated_at=? where id=?",
        Timestamp.from(Instant.now()),
        id);
    audit.add(actors.current().id(), "DeliveryTrip", id, "DISPATCH", Map.of());
    return detail(id);
  }

  public Object deliver(
      Long id, Long stopId, Long proof, Map<Long, java.math.BigDecimal> quantities, String note) {
    var t = trip(id, true);
    var s = sql.row("delivery_stop", stopId, true);
    BusinessException.require(
        SqlRecords.id(s, "trip_id") == id.longValue()
            && t.get("status").equals("DELIVERING")
            && s.get("status").equals("DELIVERING"),
        "Điểm giao không thuộc chuyến hoặc đã hoàn tất.");
    storage.assertImage(proof, SqlRecords.id(t, "npp_id"));
    if (s.get("channel").equals("SELL_IN"))
      sellin.delivery(SqlRecords.id(s, "order_id"), "delivered");
    else sales.transition(SqlRecords.id(s, "order_id"), "receipt", quantities, note);
    var now = Timestamp.from(Instant.now());
    jdbc.update(
        "update delivery_stop set"
            + " status='DELIVERED',proof_attachment_id=?,note=?,delivered_at=?,version=version+1,updated_at=?"
            + " where id=?",
        proof,
        note,
        now,
        now,
        stopId);
    if (jdbc.queryForObject(
            "select count(*) from delivery_stop where trip_id=? and status<>'DELIVERED'",
            Integer.class,
            id)
        == 0)
      jdbc.update(
          "update delivery_trip set status='COMPLETED',version=version+1,updated_at=? where id=?",
          now,
          id);
    audit.add(
        actors.current().id(),
        "DeliveryTrip",
        id,
        "DELIVER_STOP",
        Map.of("stopId", stopId, "proof", proof));
    return detail(id);
  }
}
