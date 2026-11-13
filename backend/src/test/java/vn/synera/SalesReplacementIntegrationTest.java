package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:replacement_flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesReplacementIntegrationTest {
  @Autowired vn.synera.iam.SystemWorkFacade systemWork;
  @Autowired vn.synera.sellin.SellInFacade sellin;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired vn.synera.notify.OutboxProcessor outbox;
  @Autowired vn.synera.notify.EventFacade events;

  @Test
  void optionalOrderReasonsStillNotifySalesAndPreserveReceiptQuantities() throws Exception {
    String sales = login("sales@synera.local"), npp = login("npp@synera.local");
    var order =
        post(
            sales,
            "/api/v1/sales-orders",
            Map.of("storeId", 1, "lines", List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 2))));
    long id = order.path("id").asLong(), line = order.path("lines").get(0).path("id").asLong();
    post(
        npp,
        "/api/v1/sales-orders/" + id + "/confirm",
        Map.of("quantities", Map.of(String.valueOf(line), 2)));
    long event =
        jdbc.queryForObject(
            "select max(id) from domain_event_outbox where aggregate_id=? and"
                + " event_type='SalesOrderUpdated'",
            Long.class,
            id);
    outbox.process(event);
    boolean notified = false;
    for (var notice : get(sales, "/api/v1/notifications"))
      if (notice.path("type").asText().equals("SalesOrderUpdated")) {
        var payload = json.readTree(notice.path("payload").asText());
        if (payload.path("orderId").asLong() == id
            && payload.path("status").asText().equals("NPP_CONFIRMED")) notified = true;
      }
    assertTrue(notified, "Sales must receive a persisted confirmation notice");
    post(npp, "/api/v1/sales-orders/" + id + "/ship", Map.of());
    post(npp, "/api/v1/sales-orders/" + id + "/dispatch", Map.of());
    var receipt =
        post(
            npp,
            "/api/v1/sales-orders/" + id + "/receipt",
            Map.of("quantities", Map.of(String.valueOf(line), 1)));
    assertEquals("DELIVERED_SHORT", receipt.path("status").asText());
    assertEquals(1, receipt.path("lines").get(0).path("qtyReceived").intValue());
  }

  @Test
  void sellInShortReceiptNeedsNoReasonButStillCreatesDiscrepancy() throws Exception {
    String npp = login("npp@synera.local"), admin = login("admin@synera.local");
    var order =
        post(
            npp,
            "/api/v1/sell-in-orders",
            Map.of("nppId", 2, "lines", List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 2))));
    long id = order.path("id").asLong(), line = order.path("lines").get(0).path("id").asLong();
    String path = "/api/v1/sell-in-orders/" + id;
    var submitted = post(npp, path + "/submit", Map.of());
    line = submitted.path("lines").get(0).path("id").asLong();
    post(admin, path + "/approve", Map.of());
    post(admin, path + "/confirm", Map.of("quantities", Map.of(String.valueOf(line), 2)));
    post(admin, path + "/ship", Map.of());
    post(admin, path + "/dispatch", Map.of());
    post(admin, path + "/delivered", Map.of());
    var received =
        post(
            npp,
            path + "/receipt",
            Map.of(
                "lines",
                List.of(Map.of("lineId", line, "received", 1, "damaged", 0, "returned", 0))));
    assertEquals("RECEIVED_DISCREPANCY", received.path("status").asText());
    assertTrue(
        jdbc.queryForObject(
                "select count(*) from ticket where order_id=? and order_channel='SELL_IN'",
                Integer.class,
                id)
            > 0);
  }

  JsonNode call(String token, String method, String path, Object body, int expected, String key)
      throws Exception {
    var req =
        MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
            .contentType(MediaType.APPLICATION_JSON);
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (key != null) req.header("Idempotency-Key", key);
    if (body != null) req.content(json.writeValueAsBytes(body));
    var response = mvc.perform(req).andReturn().getResponse();
    assertEquals(expected, response.getStatus(), response.getContentAsString());
    return json.readTree(response.getContentAsByteArray()).path("data");
  }

  JsonNode post(String token, String path, Object body) throws Exception {
    return call(token, "POST", path, body, 200, UUID.randomUUID().toString());
  }

  JsonNode get(String token, String path) throws Exception {
    return call(token, "GET", path, null, 200, null);
  }

  String login(String name) throws Exception {
    var r =
        post(null, "/api/v1/auth/login", Map.of("username", name, "password", "TestPassword2026!"));
    if (r.path("requiresOtp").asBoolean())
      r =
          post(
              null,
              "/api/v1/auth/otp/verify",
              Map.of(
                  "challengeId",
                  r.path("challengeId").asLong(),
                  "code",
                  r.path("localOtp").asText()));
    return r.path("accessToken").asText();
  }

  @Test
  void replacementPreservesMoneyScopesRetriesStockAndCustomerFollowUp() throws Exception {
    String sales = login("sales@synera.local"),
        npp = login("npp@synera.local"),
        other = login("npp-b@synera.local");
    long nppUser = get(npp, "/api/v1/auth/me").path("id").asLong();
    var original =
        post(
            sales,
            "/api/v1/sales-orders",
            Map.of("storeId", 1, "lines", List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 3))));
    long order = original.path("id").asLong(),
        line = original.path("lines").get(0).path("id").asLong();
    var event =
        jdbc.queryForObject(
            "select max(id) from domain_event_outbox where event_type='OrderCreated' and"
                + " aggregate_id=?",
            Long.class,
            order);
    outbox.process(event);
    outbox.process(event);
    assertTrue(get(npp, "/api/v1/notifications").size() > 0);
    assertEquals(
        1L,
        jdbc.queryForObject(
            "select count(*) from notification where user_id=? and event_key=(select event_id from"
                + " domain_event_outbox where id=?)",
            Long.class,
            nppUser,
            event));
    post(
        npp,
        "/api/v1/sales-orders/" + order + "/confirm",
        Map.of("quantities", Map.of(String.valueOf(line), 3)));
    post(npp, "/api/v1/sales-orders/" + order + "/ship", Map.of());
    post(npp, "/api/v1/sales-orders/" + order + "/dispatch", Map.of());
    post(
        npp,
        "/api/v1/sales-orders/" + order + "/receipt",
        Map.of(
            "quantities",
            Map.of(String.valueOf(line), 1),
            "reason",
            "Receiver recorded a shortage"));
    long warehouse =
        jdbc.queryForObject("select warehouse_id from sales_order where id=?", Long.class, order);
    BigDecimal stock =
        jdbc.queryForObject(
            "select sum(qty_on_hand) from stock_balance where warehouse_id=? and sku_id=1",
            BigDecimal.class,
            warehouse);
    long orderCount = jdbc.queryForObject("select count(*) from sales_order", Long.class);
    long ledgerCount = jdbc.queryForObject("select count(*) from credit_ledger", Long.class);
    String path = "/api/v1/sales-replacements";
    var request =
        Map.of(
            "orderId",
            order,
            "quantities",
            Map.of(String.valueOf(line), 2),
            "dueAt",
            Instant.now().plusSeconds(3600).toString(),
            "reason",
            "Replace confirmed missing goods");
    String key = UUID.randomUUID().toString();
    var plan = call(sales, "POST", path, request, 200, key);
    long id = plan.path("id").asLong();
    assertEquals(id, call(sales, "POST", path, request, 200, key).path("id").asLong());
    call(other, "GET", path + "/" + id, null, 404, null);
    call(
        sales,
        "POST",
        path + "/" + id + "/approve",
        Map.of("reason", "not authorized"),
        403,
        UUID.randomUUID().toString());
    call(npp, "POST", path, request, 422, UUID.randomUUID().toString());
    post(npp, path + "/" + id + "/approve", Map.of("reason", "Approved within shortage"));
    post(npp, path + "/" + id + "/ship", Map.of("reason", "Additional stock shipped"));
    var replacementLine = get(npp, path + "/" + id).path("lines").get(0);
    var received =
        Map.of(
            "reason",
            "Receiver confirmed replacement",
            "quantities",
            Map.of(replacementLine.path("id").asText(), 2));
    String receiptKey = UUID.randomUUID().toString();
    call(npp, "POST", path + "/" + id + "/receipt", received, 200, receiptKey);
    call(npp, "POST", path + "/" + id + "/receipt", received, 200, receiptKey);
    var beforeFeedback = get(sales, path + "/" + id);
    assertEquals(id, get(sales, path + "?orderId=" + order).get(0).path("id").asLong());
    call(other, "GET", path + "?orderId=" + order, null, 404, null);
    assertTrue(beforeFeedback.path("notifiedAt").isNull());
    var shortEvents =
        jdbc.queryForList(
            "select id from domain_event_outbox where event_type='DeliveryShort' and"
                + " aggregate_id=?",
            Long.class,
            order);
    for (var eventId : shortEvents) outbox.process(eventId);
    assertTrue(
        jdbc.queryForObject(
                "select count(*) from task where reference_type in"
                    + " ('DELIVERY_SHORT','SALES_DELIVERY_SHORT') and reference_id=? and"
                    + " status='OPEN'",
                Integer.class,
                order)
            > 0);
    post(
        sales,
        path + "/" + id + "/customer-feedback",
        Map.of("note", "Customer informed and confirmed receipt", "expectedStatus", "RECEIVED"));
    assertFalse(get(npp, path + "/" + id).path("notifiedAt").isNull());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from task where reference_type in"
                + " ('DELIVERY_SHORT','SALES_DELIVERY_SHORT') and reference_id=? and status='OPEN'",
            Integer.class,
            order));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from task where reference_type='SALES_REPLACEMENT' and reference_id=?"
                + " and status='OPEN'",
            Integer.class,
            id));
    assertEquals(orderCount, jdbc.queryForObject("select count(*) from sales_order", Long.class));
    events.add(
        "DeliveryShort",
        order,
        original.path("nppId").asLong(),
        original.path("repId").asLong(),
        Map.of("storeId", original.path("storeId").asLong()));
    outbox.process(
        jdbc.queryForObject(
            "select max(id) from domain_event_outbox where event_type='DeliveryShort' and"
                + " aggregate_id=?",
            Long.class,
            order));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from task where reference_type='SALES_DELIVERY_SHORT' and"
                + " reference_id=? and status='OPEN'",
            Integer.class,
            order));
    assertEquals(
        ledgerCount, jdbc.queryForObject("select count(*) from credit_ledger", Long.class));
    assertEquals(
        0,
        original
            .path("total")
            .decimalValue()
            .compareTo(get(npp, "/api/v1/sales-orders/" + order).path("total").decimalValue()));
    BigDecimal after =
        jdbc.queryForObject(
            "select sum(qty_on_hand) from stock_balance where warehouse_id=? and sku_id=1",
            BigDecimal.class,
            warehouse);
    assertEquals(0, stock.subtract(new BigDecimal("48")).compareTo(after));
    call(npp, "POST", path, request, 422, UUID.randomUUID().toString());
  }

  @Test
  void competitorsCannotAllocateTheSameShortageAndNppIsNotAssignedAsSales() throws Exception {
    String sales = login("sales@synera.local"), npp = login("npp@synera.local");
    var order =
        post(
            npp,
            "/api/v1/sales-orders",
            Map.of("storeId", 1, "lines", List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 3))));
    assertEquals(get(sales, "/api/v1/auth/me").path("id").asLong(), order.path("repId").asLong());
    long id = order.path("id").asLong(), line = order.path("lines").get(0).path("id").asLong();
    for (var action : List.of("confirm", "ship", "dispatch", "receipt"))
      post(
          npp,
          "/api/v1/sales-orders/" + id + "/" + action,
          Map.of(
              "quantities",
              Map.of(String.valueOf(line), action.equals("receipt") ? 1 : 3),
              "reason",
              "Concurrent allocation test"));
    var request =
        Map.of(
            "orderId",
            id,
            "quantities",
            Map.of(String.valueOf(line), 2),
            "dueAt",
            Instant.now().plusSeconds(3600).toString(),
            "reason",
            "Allocate outstanding shortage");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var gate = new java.util.concurrent.CyclicBarrier(2);
      java.util.List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();
      for (var token : List.of(npp, sales))
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  return mvc.perform(
                          MockMvcRequestBuilders.post("/api/v1/sales-replacements")
                              .header("Authorization", "Bearer " + token)
                              .header("Idempotency-Key", UUID.randomUUID().toString())
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(json.writeValueAsBytes(request)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      var statuses = new ArrayList<Integer>();
      for (var f : futures) statuses.add(f.get(20, java.util.concurrent.TimeUnit.SECONDS));
      Collections.sort(statuses);
      assertEquals(List.of(200, 422), statuses);
    } finally {
      pool.shutdownNow();
    }
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from sales_replacement where order_id=?", Integer.class, id));
  }

  @Test
  void overdueReceiptIsReminderOnlyByDefault() throws Exception {
    var npp = login("npp@synera.local");
    var order =
        post(
            npp,
            "/api/v1/sell-in-orders",
            Map.of("nppId", 2, "lines", List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 1))));
    long id = order.path("id").asLong();
    jdbc.update(
        "update sell_in_order set status='DELIVERED',delivered_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(100 * 3600)),
        id);
    systemWork.run(sellin::autoReceipts);
    assertEquals("DELIVERED", get(npp, "/api/v1/sell-in-orders/" + id).path("status").asText());
    assertEquals(
        0,
        get(npp, "/api/v1/sell-in-orders/" + id)
            .path("lines")
            .get(0)
            .path("qtyReceived")
            .decimalValue()
            .signum());
  }

  @Test
  void partialReplacementCanBeReplannedAndSelfApprovalIsRejected() throws Exception {
    String sales = login("sales@synera.local"),
        npp = login("npp@synera.local"),
        admin = login("admin@synera.local");
    var order =
        post(
            sales,
            "/api/v1/sales-orders",
            Map.of("storeId", 1, "lines", List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 3))));
    long id = order.path("id").asLong(), line = order.path("lines").get(0).path("id").asLong();
    for (var action : List.of("confirm", "ship", "dispatch", "receipt"))
      post(
          npp,
          "/api/v1/sales-orders/" + id + "/" + action,
          Map.of(
              "quantities",
              Map.of(String.valueOf(line), action.equals("receipt") ? 1 : 3),
              "reason",
              "Receiver recorded discrepancy"));
    var body =
        Map.of(
            "orderId",
            id,
            "quantities",
            Map.of(String.valueOf(line), 2),
            "dueAt",
            Instant.now().plusSeconds(7200).toString(),
            "reason",
            "Replace missing quantity");
    var replacement = post(npp, "/api/v1/sales-replacements", body);
    long plan = replacement.path("id").asLong();
    String path = "/api/v1/sales-replacements/" + plan;
    call(
        npp,
        "POST",
        path + "/approve",
        Map.of("reason", "creator cannot approve"),
        422,
        UUID.randomUUID().toString());
    post(admin, path + "/approve", Map.of("reason", "independent approval"));
    post(npp, path + "/ship", Map.of("reason", "replacement dispatched"));
    post(
        npp,
        path + "/receipt",
        Map.of(
            "quantities",
            Map.of(replacement.path("lines").get(0).path("id").asText(), 1),
            "reason",
            "Still short one unit"));
    var detail = get(npp, "/api/v1/sales-orders/" + id);
    assertTrue(detail.path("hasOutstandingShortage").asBoolean());
    assertEquals(1, detail.path("lines").get(0).path("qtyAvailableToReplace").intValue());
    var taskId =
        jdbc.queryForObject(
            "select id from task where reference_type='SALES_REPLACEMENT' and reference_id=?",
            Long.class,
            plan);
    call(npp, "POST", "/api/v1/tasks/" + taskId + "/complete", Map.of(), 422, null);
    var second =
        post(
            sales,
            "/api/v1/sales-replacements",
            Map.of(
                "orderId",
                id,
                "quantities",
                Map.of(String.valueOf(line), 1),
                "dueAt",
                Instant.now().plusSeconds(7200).toString(),
                "reason",
                "Remaining shortage"));
    String secondPath = "/api/v1/sales-replacements/" + second.path("id").asLong();
    post(
        sales,
        secondPath + "/customer-feedback",
        Map.of("note", "Customer told about proposed arrangement", "expectedStatus", "PROPOSED"));
    post(
        npp,
        "/api/v1/sales-replacements/" + second.path("id").asLong() + "/cancel",
        Map.of("reason", "Need to revise delivery arrangement"));
    assertTrue(get(sales, secondPath).path("notifiedAt").isNull());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from task where reference_type='SALES_REPLACEMENT' and reference_id=?"
                + " and status='OPEN'",
            Integer.class,
            second.path("id").asLong()));
    call(
        sales,
        "POST",
        secondPath + "/customer-feedback",
        Map.of("note", "Stale response", "expectedStatus", "PROPOSED"),
        409,
        UUID.randomUUID().toString());
    post(
        sales,
        secondPath + "/customer-feedback",
        Map.of(
            "note",
            "Customer informed of cancellation and next action",
            "expectedStatus",
            "CANCELLED"));
    assertEquals(4, get(sales, secondPath).path("history").size());
    assertEquals(
        1,
        get(npp, "/api/v1/sales-orders/" + id)
            .path("lines")
            .get(0)
            .path("qtyAvailableToReplace")
            .intValue());
    assertEquals(4, get(npp, path).path("history").size());
  }
}
