package vn.synera.notify;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxProcessor {
  private final JdbcTemplate jdbc;
  private final vn.synera.iam.RoleFacade roles;
  private final vn.synera.catalog.CatalogFacade catalog;
  private final vn.synera.common.Jsons json;
  private final vn.synera.salesorder.ReplacementFacade replacements;
  private final TaskFacade tasks;

  public OutboxProcessor(
      JdbcTemplate jdbc,
      vn.synera.iam.RoleFacade roles,
      vn.synera.catalog.CatalogFacade catalog,
      vn.synera.common.Jsons json,
      vn.synera.salesorder.ReplacementFacade replacements,
      TaskFacade tasks) {
    this.jdbc = jdbc;
    this.roles = roles;
    this.catalog = catalog;
    this.json = json;
    this.replacements = replacements;
    this.tasks = tasks;
  }

  @Transactional
  public void process(Long id) {
    var rows = jdbc.queryForList("select * from domain_event_outbox where id=? for update", id);
    if (rows.isEmpty()) return;
    var r = rows.get(0);
    if (!r.get("status").equals("PENDING")) return;
    String event = (String) r.get("event_id");
    boolean done =
        !jdbc.queryForList(
                "select event_id from processed_event where consumer='in-app' and event_id=?",
                event)
            .isEmpty();
    if (!done) {
      var now = Timestamp.from(Instant.now());
      var recipients = new java.util.LinkedHashSet<Long>();
      if (r.get("user_id") != null) recipients.add(((Number) r.get("user_id")).longValue());
      java.util.Set<String> targetRoles =
          switch ((String) r.get("event_type")) {
            case "OrderCreated" -> {
              var payload = (java.util.Map<?, ?>) json.read((String) r.get("payload"));
              yield "SELL_OUT".equals(payload.get("channel"))
                  ? java.util.Set.of("NPP_OWNER", "WAREHOUSE_NPP")
                  : java.util.Set.of("REGION_MANAGER", "ACCOUNTANT_NSX", "SYSTEM_ADMIN");
            }
            case "SalesOrderUpdated",
                "ReplacementProposed",
                "ReplacementUpdated",
                "ReplacementCustomerUpdated" ->
                java.util.Set.of("NPP_OWNER", "WAREHOUSE_NPP", "CUSTOMER_SERVICE");
            case "PaymentConfirmed", "ReturnPosted" ->
                java.util.Set.of("NPP_OWNER", "ACCOUNTANT_NPP", "ACCOUNTANT_NSX");
            case "DeliveryShort" -> java.util.Set.of("CUSTOMER_SERVICE", "NPP_OWNER", "SUPERVISOR");
            case "ReceiptReminder" -> java.util.Set.of("NPP_OWNER", "WAREHOUSE_NPP");
            case "OrderStatusChanged" -> {
              var payload = (java.util.Map<?, ?>) json.read((String) r.get("payload"));
              yield java.util.Set.of("PENDING_APPROVAL", "ON_HOLD_CREDIT")
                      .contains(payload.get("status"))
                  ? java.util.Set.of("REGION_MANAGER", "ACCOUNTANT_NSX", "SYSTEM_ADMIN")
                  : java.util.Set.of("NPP_OWNER", "WAREHOUSE_NPP");
            }
            default -> java.util.Set.of();
          };
      if (r.get("npp_id") != null && !targetRoles.isEmpty()) {
        Long npp = ((Number) r.get("npp_id")).longValue();
        String region = catalog.notificationRegion(npp);
        for (Long user :
            jdbc.queryForList("select id from app_user where enabled=true", Long.class))
          if (roles.workspaces(user).stream()
              .anyMatch(
                  w ->
                      targetRoles.contains(w.role())
                          && (java.util.Objects.equals(w.nppId(), npp)
                              || vn.synera.iam.Permissions.nsx(w.role())
                                  && (w.region() == null || w.region().equals(region)))))
            recipients.add(user);
      }
      for (Long user : recipients) {
        jdbc.update(
            "insert into"
                + " notification(created_at,updated_at,version,user_id,npp_id,type,payload,event_key)"
                + " values(?,?,0,?,?,?,?,?)",
            now,
            now,
            user,
            r.get("npp_id"),
            r.get("event_type"),
            r.get("payload"),
            event);
        if (r.get("event_type").equals("DeliveryShort"))
          jdbc.update(
              "insert into"
                  + " task(created_at,updated_at,version,npp_id,assignee_id,title,due_at,status,reference_type,reference_id)"
                  + " values(?,?,0,?,?,?,?,'OPEN',?,?)",
              now,
              now,
              r.get("npp_id"),
              user,
              "Theo dõi giao thiếu #" + r.get("aggregate_id"),
              Timestamp.from(Instant.now().plusSeconds(86400)),
              ((java.util.Map<?, ?>) json.read((String) r.get("payload"))).containsKey("storeId")
                  ? "SALES_DELIVERY_SHORT"
                  : "DELIVERY_SHORT",
              r.get("aggregate_id"));
      }
      jdbc.update(
          "insert into processed_event(consumer,event_id,processed_at) values('in-app',?,?)",
          event,
          now);
    }
    var payload = (java.util.Map<?, ?>) json.read((String) r.get("payload"));
    if (r.get("event_type").equals("DeliveryShort") && payload.containsKey("storeId"))
      replacements.reconcileFollowUp(((Number) r.get("aggregate_id")).longValue());
    if (((String) r.get("event_type")).startsWith("Replacement")
        && payload.get("orderId") instanceof Number order)
      replacements.reconcileFollowUp(order.longValue());
    jdbc.update("update domain_event_outbox set status='DONE' where id=?", id);
  }

  @Transactional
  public int reminders() {
    // Repair old open roots after upgrade using the same goods + customer checks.
    for (var order : tasks.pendingDeliveryShortOrders()) replacements.reconcileFollowUp(order);
    int count = 0;
    for (var r :
        jdbc.queryForList(
            "select id,npp_id,rep_id,title from appointment where status='OPEN' and reminded=false"
                + " and remind_at<=?",
            Timestamp.from(Instant.now()))) {
      String event = "appointment-" + r.get("id");
      var now = Timestamp.from(Instant.now());
      int changed =
          jdbc.update(
              "update appointment set reminded=true,version=version+1,updated_at=? where id=? and"
                  + " reminded=false",
              now,
              r.get("id"));
      if (changed == 1) {
        jdbc.update(
            "insert into"
                + " notification(created_at,updated_at,version,user_id,npp_id,type,payload,event_key)"
                + " values(?,?,0,?,?,'APPOINTMENT_DUE',?,?)",
            now,
            now,
            r.get("rep_id"),
            r.get("npp_id"),
            r.get("title"),
            event);
        count++;
      }
    }
    return count;
  }
}
