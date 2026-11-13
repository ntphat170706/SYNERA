package vn.synera.closing;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;

@Service
public class OperationalFacade {
  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;

  public OperationalFacade(
      JdbcTemplate jdbc,
      SqlRecords sql,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
  }

  private void detect(
      String key,
      String category,
      Long npp,
      Long warehouse,
      Long sku,
      Long store,
      String detail,
      Set<String> active) {
    active.add(key);
    var old = jdbc.queryForList("select id,status from operational_alert where alert_key=?", key);
    if (old.isEmpty())
      sql.insert(
          "operational_alert",
          SqlRecords.mutable(
              "alert_key",
              key,
              "category",
              category,
              "npp_id",
              npp,
              "warehouse_id",
              warehouse,
              "sku_id",
              sku,
              "store_id",
              store,
              "detail",
              detail,
              "detected_at",
              Timestamp.from(Instant.now())));
    else if (old.get(0).get("status").equals("RESOLVED"))
      jdbc.update(
          "update operational_alert set status='OPEN',detail=?,detected_at=?,resolved_at=null where"
              + " id=?",
          detail,
          Timestamp.from(Instant.now()),
          old.get(0).get("id"));
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public int scan() {
    var active = new HashSet<String>();
    for (var row :
        jdbc.queryForList(
            "select a.npp_id,a.balance,coalesce(l.expected,0) expected from credit_limit a left"
                + " join (select npp_id,sum(debit-credit) expected from credit_ledger group by"
                + " npp_id) l on l.npp_id=a.npp_id"))
      if (((BigDecimal) row.get("balance")).compareTo((BigDecimal) row.get("expected")) != 0)
        detect(
            "CREDIT:" + row.get("npp_id"),
            "CREDIT_LEDGER_DRIFT",
            SqlRecords.id(row, "npp_id"),
            null,
            null,
            null,
            "Số dư công nợ khác tổng sổ cái. Cần kiểm tra chứng từ; hệ thống không tự sửa sổ.",
            active);
    for (var row :
        jdbc.queryForList(
            "select b.*,w.org_id,o.type org_type,coalesce(m.expected,0)"
                + " expected,coalesce(a.reserved,0) allocated from stock_balance b join warehouse w"
                + " on w.id=b.warehouse_id join organization o on o.id=w.org_id left join (select"
                + " warehouse_id,sku_id,lot_no,sum(qty) expected from stock_movement group by"
                + " warehouse_id,sku_id,lot_no) m on m.warehouse_id=b.warehouse_id and"
                + " m.sku_id=b.sku_id and m.lot_no=b.lot_no left join (select lot_id,sum(qty)"
                + " reserved from stock_allocation where shipped=false group by lot_id) a on"
                + " a.lot_id=b.id")) {
      Long npp = row.get("org_type").equals("NPP") ? SqlRecords.id(row, "org_id") : null;
      Long wh = SqlRecords.id(row, "warehouse_id"), sku = SqlRecords.id(row, "sku_id");
      if (((BigDecimal) row.get("qty_on_hand")).compareTo((BigDecimal) row.get("expected")) != 0)
        detect(
            "STOCK:" + row.get("id"),
            "STOCK_LEDGER_DRIFT",
            npp,
            wh,
            sku,
            null,
            "Tồn theo lô khác tổng phiếu biến động.",
            active);
      if (((BigDecimal) row.get("qty_reserved")).compareTo((BigDecimal) row.get("allocated")) != 0)
        detect(
            "RESERVED:" + row.get("id"),
            "STOCK_RESERVATION_DRIFT",
            npp,
            wh,
            sku,
            null,
            "Tồn giữ khác tổng phân bổ chưa xuất.",
            active);
      if (((BigDecimal) row.get("qty_on_hand")).signum() > 0
          && row.get("expiry_date") instanceof java.sql.Date date
          && !date.toLocalDate().isAfter(Dates.today().plusDays(30)))
        detect(
            "EXPIRY:" + row.get("id"),
            "STOCK_EXPIRY",
            npp,
            wh,
            sku,
            null,
            "Lô " + row.get("lot_no") + " hết hạn vào " + date + "; kiểm tra trước khi xuất.",
            active);
    }
    for (var row :
        jdbc.queryForList(
            "select b.warehouse_id,b.sku_id,w.org_id,o.type"
                + " org_type,sum(b.qty_on_hand-b.qty_reserved) available,s.min_order from"
                + " stock_balance b join sku s on s.id=b.sku_id join warehouse w on"
                + " w.id=b.warehouse_id join organization o on o.id=w.org_id where"
                + " w.type<>'QUARANTINE' and (b.expiry_date is null or b.expiry_date>=?) group by"
                + " b.warehouse_id,b.sku_id,w.org_id,o.type,s.min_order",
            java.sql.Date.valueOf(Dates.today())))
      if (((BigDecimal) row.get("available")).compareTo((BigDecimal) row.get("min_order")) < 0)
        detect(
            "LOW:" + row.get("warehouse_id") + ":" + row.get("sku_id"),
            "LOW_STOCK",
            row.get("org_type").equals("NPP") ? SqlRecords.id(row, "org_id") : null,
            SqlRecords.id(row, "warehouse_id"),
            SqlRecords.id(row, "sku_id"),
            null,
            "Tồn khả dụng thấp hơn lượng đặt tối thiểu của sản phẩm.",
            active);
    for (var row :
        jdbc.queryForList(
            "select s.id,s.npp_id from store s left join (select store_id,max(delivered_at)"
                + " last_order from sales_order where status in ('DELIVERED','DELIVERED_SHORT')"
                + " group by store_id) o on o.store_id=s.id where s.status='ACTIVE' and"
                + " coalesce(o.last_order,s.created_at)<?",
            Timestamp.from(Dates.start(Dates.today().minusDays(60)))))
      detect(
          "CHURN:" + row.get("id"),
          "STORE_AT_RISK",
          SqlRecords.id(row, "npp_id"),
          null,
          null,
          SqlRecords.id(row, "id"),
          "Cửa hàng chưa có đơn giao trong 60 ngày; cần chăm sóc.",
          active);
    for (var old :
        jdbc.queryForList("select id,alert_key from operational_alert where status<>'RESOLVED'"))
      if (!active.contains(old.get("alert_key")))
        jdbc.update(
            "update operational_alert set status='RESOLVED',resolved_at=? where id=?",
            Timestamp.from(Instant.now()),
            old.get("id"));
    return active.size();
  }

  public List<Map<String, Object>> list() {
    var visible = new ArrayList<Map<String, Object>>();
    var npps = catalog.visibleNppIds();
    var warehouses = catalog.warehouses().stream().map(CatalogFacade.WarehouseInfo::id).toList();
    for (var row :
        jdbc.queryForList(
            "select * from operational_alert where status<>'RESOLVED' order by id desc limit"
                + " 2000")) {
      boolean scope =
          row.get("npp_id") != null
              ? npps.contains(SqlRecords.id(row, "npp_id"))
              : row.get("warehouse_id") != null
                  && warehouses.contains(SqlRecords.id(row, "warehouse_id"));
      if (scope
          && (!actors.current().sales()
              || row.get("store_id") != null
                  && catalog.stores("").stream()
                      .anyMatch(s -> s.id() == SqlRecords.id(row, "store_id")))) visible.add(row);
    }
    return visible;
  }

  @Transactional
  public Object acknowledge(Long id, String reason) {
    actors.require("REPORT_READ");
    BusinessException.require(
        list().stream().anyMatch(r -> SqlRecords.id(r, "id") == id.longValue()),
        "Không có cảnh báo trong phạm vi hoặc thiếu lý do.");
    jdbc.update(
        "update operational_alert set status='ACKNOWLEDGED' where id=? and status='OPEN'", id);
    audit.add(
        actors.current().id(), "OperationalAlert", id, "ACKNOWLEDGE", Map.of("reason", reason));
    return Map.of("id", id, "status", "ACKNOWLEDGED");
  }
}
