package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiIntegrationTest {
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired vn.synera.notify.OutboxProcessor outbox;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  String admin, npp, sales, other;
  long repId;
  long fullOrder;
  Map<String, Object> offlinePayload;
  BigDecimal invoiceAmount;
  @Autowired vn.synera.report.ReportExportFacade reportExports;
  @Autowired vn.synera.iam.SystemWorkFacade systemWork;
  @Autowired vn.synera.closing.OperationalFacade operations;
  final String password = "TestPassword2026!";

  String key() {
    return UUID.randomUUID().toString();
  }

  JsonNode call(String token, String method, String url, Object body, int expected, String key)
      throws Exception {
    var request =
        MockMvcRequestBuilders.request(HttpMethod.valueOf(method), url)
            .contentType(MediaType.APPLICATION_JSON);
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (key != null) request.header("Idempotency-Key", key);
    if (body != null) request.content(json.writeValueAsBytes(body));
    var result = mvc.perform(request).andReturn();
    String content =
        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(expected, result.getResponse().getStatus(), method + " " + url + ": " + content);
    return json.readTree(result.getResponse().getContentAsByteArray());
  }

  JsonNode get(String token, String path) throws Exception {
    return call(token, "GET", path, null, 200, null).path("data");
  }

  JsonNode post(String token, String path, Object body) throws Exception {
    return call(token, "POST", path, body, 200, key()).path("data");
  }

  String login(String username) throws Exception {
    var data = post(null, "/api/v1/auth/login", Map.of("username", username, "password", password));
    if (data.path("requiresOtp").asBoolean())
      data =
          post(
              null,
              "/api/v1/auth/otp/verify",
              Map.of(
                  "challengeId",
                  data.path("challengeId").asLong(),
                  "code",
                  data.path("localOtp").asText()));
    return data.path("accessToken").asText();
  }

  @Test
  @Order(99)
  void personalSalesSummaryUsesReceivedQuantitiesAndOwnScope() throws Exception {
    var from = LocalDate.now(vn.synera.common.Dates.VN).minusDays(30);
    var to = LocalDate.now(vn.synera.common.Dates.VN);
    var endpoint = "/api/v1/sales-orders/summary?from=" + from + "&to=" + to;
    var summary = get(sales, endpoint);
    assertEquals(repId, summary.path("repId").asLong());
    assertEquals("PROVISIONAL", summary.path("layer").asText());
    var nppId = get(sales, "/api/v1/auth/me").path("nppId").asLong();
    var params =
        new Object[] {
          repId,
          nppId,
          java.sql.Timestamp.from(vn.synera.common.Dates.start(from)),
          java.sql.Timestamp.from(vn.synera.common.Dates.start(to.plusDays(1)))
        };
    var value =
        jdbc.queryForObject(
            "select coalesce(sum(total),0) from sales_order where rep_id=? and npp_id=? and"
                + " created_at>=? and created_at<? and status<>'CANCELLED'",
            BigDecimal.class,
            params);
    assertEquals(0, value.compareTo(summary.path("orderValue").decimalValue()));
    var received =
        jdbc.queryForObject(
            "select"
                + " coalesce(sum(round((l.qty_ordered*l.price-l.discount)*l.qty_received/l.qty_ordered,2)),0)"
                + " from sales_order o join sales_order_line l on l.order_id=o.id where o.rep_id=?"
                + " and o.npp_id=? and o.delivered_at>=? and o.delivered_at<? and o.status in"
                + " ('DELIVERED','DELIVERED_SHORT')",
            BigDecimal.class,
            params);
    assertEquals(0, received.compareTo(summary.path("receivedSales").decimalValue()));
    call(npp, "GET", endpoint, null, 403, null);
    call(admin, "GET", endpoint, null, 403, null);
    call(sales, "GET", "/api/v1/sales-orders/summary?from=" + to + "&to=" + from, null, 422, null);
    call(sales, "GET", endpoint + "&repId=999&nppId=999", null, 200, null);
    assertEquals(repId, get(sales, endpoint + "&repId=999").path("repId").asLong());
  }

  @BeforeAll
  void loginUsers() throws Exception {
    admin = login("admin@synera.local");
    npp = login("npp@synera.local");
    sales = login("sales@synera.local");
    other = login("npp-b@synera.local");
    repId = get(sales, "/api/v1/auth/me").path("id").asLong();
  }

  Map<String, Object> line(int qty) {
    return Map.of("skuId", 1, "uom", "THUNG", "qty", qty);
  }

  JsonNode draft(String owner, int qty) throws Exception {
    return post(owner, "/api/v1/sell-in-orders", Map.of("nppId", 2, "lines", List.of(line(qty))));
  }

  JsonNode currentCredit() throws Exception {
    for (var a : get(admin, "/api/v1/credit")) if (a.path("nppId").asLong() == 2) return a;
    throw new AssertionError("Account missing");
  }

  void setLimit(BigDecimal amount) throws Exception {
    post(
        admin,
        "/api/v1/credit/2/limit",
        Map.of("amount", amount, "reason", "Integration test limit"));
  }

  @Test
  @Order(1)
  void unauthenticatedCannotRead() throws Exception {
    call(null, "GET", "/api/v1/stores", null, 401, null);
  }

  @Test
  @Order(2)
  void storeProfilePaginatesEachSectionWithoutLeakingAnotherNpp() throws Exception {
    for (int i = 0; i < 11; i++)
      post(
          sales,
          "/api/v1/interactions",
          Map.of("storeId", 1, "kind", "NOTE", "note", "Profile page " + i));
    var first = get(sales, "/api/v1/stores/1/360/interactions?page=0&size=3");
    var second = get(sales, "/api/v1/stores/1/360/interactions?page=1&size=3");
    assertEquals(3, first.path("content").size());
    assertEquals(11, first.path("totalElements").asInt());
    assertTrue(first.path("hasNext").asBoolean());
    assertTrue(
        first.path("content").get(2).path("id").asLong()
            > second.path("content").get(0).path("id").asLong());
    for (String section : List.of("orders", "interactions", "appointments", "tickets", "display")) {
      assertTrue(get(npp, "/api/v1/stores/1/360/" + section).path("content").isArray());
      call(other, "GET", "/api/v1/stores/1/360/" + section, null, 404, null);
    }
    call(sales, "GET", "/api/v1/stores/1/360/interactions?size=0", null, 422, null);
    call(sales, "GET", "/api/v1/stores/1/360/unknown", null, 422, null);
  }

  @Test
  @Order(11)
  void policyWorkflowPersistsIntermediateApprovalAndPreventsSelfSecondApproval() throws Exception {
    post(
        admin,
        "/api/v1/admin/workflows",
        Map.of(
            "type",
            "POLICY",
            "steps",
            List.of(
                Map.of("step", 1, "role", "POLICY_WRITE", "minAmount", 0, "slaHours", 24),
                Map.of("step", 2, "role", "NSX_DIRECTOR", "minAmount", 500, "slaHours", 24)),
            "reason",
            "Kiểm thử duyệt hai cấp"));
    long promotion =
        post(
                admin,
                "/api/v1/policies",
                Map.of(
                    "name",
                    "Chính sách thử nghiệm hai cấp",
                    "nppId",
                    2,
                    "percent",
                    5,
                    "budget",
                    1000,
                    "minQty",
                    0,
                    "minAmount",
                    0,
                    "priority",
                    1,
                    "startDate",
                    vn.synera.common.Dates.today().plusDays(1).toString(),
                    "endDate",
                    vn.synera.common.Dates.today().plusDays(2).toString()))
            .path("id")
            .asLong();
    post(
        admin,
        "/api/v1/policies/" + promotion + "/conditions",
        Map.of(
            "requiredBase",
            Map.of("1", 24, "2", 6),
            "maxUses",
            1,
            "reason",
            "Combo và giới hạn lượt"));
    assertEquals(
        "PENDING_APPROVAL",
        post(admin, "/api/v1/policies/" + promotion + "/publish", Map.of("reason", "Duyệt cấp một"))
            .path("status")
            .asText());
    call(
        admin,
        "POST",
        "/api/v1/policies/" + promotion + "/publish",
        Map.of("reason", "Không được tự duyệt cấp hai"),
        403,
        key());
    String email = "policy-director-" + key() + "@test.local";
    post(
        admin,
        "/api/v1/admin/users",
        Map.of(
            "username",
            email,
            "name",
            "Giám đốc duyệt chính sách",
            "password",
            password,
            "role",
            "NSX_DIRECTOR"));
    String director = login(email);
    assertEquals(
        "ACTIVE",
        post(
                director,
                "/api/v1/policies/" + promotion + "/publish",
                Map.of("reason", "Duyệt độc lập cấp hai"))
            .path("status")
            .asText());
    post(
        admin,
        "/api/v1/admin/workflows",
        Map.of(
            "type",
            "POLICY",
            "steps",
            List.of(Map.of("step", 1, "role", "TRADE_MARKETING", "minAmount", 0, "slaHours", 24)),
            "reason",
            "Khôi phục cấu hình mặc định"));
  }

  @Test
  @Order(11)
  void quotationCampaignAndSurveysUseAuthoritativeScope() throws Exception {
    long quote =
        post(
                sales,
                "/api/v1/quotations",
                Map.of(
                    "storeId",
                    1,
                    "validUntil",
                    LocalDate.now(vn.synera.common.Dates.VN).plusDays(2).toString(),
                    "lines",
                    List.of(line(1))))
            .path("id")
            .asLong();
    post(
        sales,
        "/api/v1/quotations/" + quote + "/transition",
        Map.of("status", "SENT", "reason", "Gửi khách kiểm tra"));
    var accepted =
        post(
            sales,
            "/api/v1/quotations/" + quote + "/transition",
            Map.of("status", "ACCEPTED", "reason", "Khách đồng ý"));
    assertTrue(accepted.path("sales_order_id").asLong() > 0);
    call(
        other,
        "POST",
        "/api/v1/quotations/" + quote + "/transition",
        Map.of("status", "CANCELLED", "reason", "forged"),
        404,
        key());
    post(
        sales,
        "/api/v1/stock-surveys",
        Map.of(
            "storeId",
            1,
            "date",
            vn.synera.common.Dates.today().toString(),
            "lines",
            List.of(Map.of("skuId", 1, "qtyBase", 5)),
            "note",
            "Đếm thực tế"));
    assertTrue(get(sales, "/api/v1/stores/1/stock-surveys").size() > 0);
    long campaign =
        post(
                npp,
                "/api/v1/campaigns",
                Map.of(
                    "nppId",
                    2,
                    "name",
                    "Chăm sóc định kỳ",
                    "groupName",
                    "",
                    "message",
                    "Kiểm tra tồn và tư vấn đặt hàng"))
            .path("id")
            .asLong();
    var launched =
        post(
            npp,
            "/api/v1/campaigns/" + campaign + "/launch",
            Map.of("dueAt", Instant.now().plusSeconds(86400).toString()));
    assertTrue(launched.path("recipients").asInt() > 0);
    call(
        npp,
        "POST",
        "/api/v1/campaigns/" + campaign + "/launch",
        Map.of("dueAt", Instant.now().plusSeconds(86400).toString()),
        422,
        key());
    assertTrue(get(sales, "/api/v1/tasks").size() > 0);
  }

  @Test
  @Order(11)
  void driverTripScopeProofAndReceiptAreIdempotent() throws Exception {
    String email = "driver-" + key() + "@test.local";
    long driver =
        post(
                admin,
                "/api/v1/admin/users",
                Map.of(
                    "username",
                    email,
                    "name",
                    "Tài xế thử nghiệm",
                    "password",
                    password,
                    "role",
                    "DRIVER",
                    "nppId",
                    2))
            .path("id")
            .asLong();
    String driverToken = login(email);
    call(driverToken, "GET", "/api/v1/stores", null, 403, null);
    call(driverToken, "GET", "/api/v1/credit", null, 403, null);
    long order =
        post(sales, "/api/v1/sales-orders", Map.of("storeId", 1, "lines", List.of(line(1))))
            .path("id")
            .asLong();
    long orderLine =
        get(npp, "/api/v1/sales-orders/" + order).path("lines").get(0).path("id").asLong();
    post(
        npp,
        "/api/v1/sales-orders/" + order + "/confirm",
        Map.of("quantities", Map.of(Long.toString(orderLine), 1)));
    post(npp, "/api/v1/sales-orders/" + order + "/ship", Map.of());
    long trip =
        post(
                npp,
                "/api/v1/delivery-trips",
                Map.of(
                    "nppId",
                    2,
                    "driverId",
                    driver,
                    "deliveryDate",
                    vn.synera.common.Dates.today().toString(),
                    "stops",
                    List.of(Map.of("channel", "SELL_OUT", "orderId", order))))
            .path("id")
            .asLong();
    call(other, "GET", "/api/v1/delivery-trips/" + trip, null, 404, null);
    var detail = get(driverToken, "/api/v1/delivery-trips/" + trip);
    assertFalse(detail.path("stops").get(0).path("lines").get(0).has("price"));
    post(driverToken, "/api/v1/delivery-trips/" + trip + "/dispatch", Map.of());
    byte[] png =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII=");
    var upload =
        mvc.perform(
                MockMvcRequestBuilders.multipart("/api/v1/attachments/upload")
                    .file(
                        new org.springframework.mock.web.MockMultipartFile(
                            "file", "proof.png", "image/png", png))
                    .param("nppId", "2")
                    .header("Authorization", "Bearer " + driverToken))
            .andReturn();
    assertEquals(200, upload.getResponse().getStatus());
    long proof =
        json.readTree(upload.getResponse().getContentAsByteArray())
            .path("data")
            .path("id")
            .asLong();
    long stop = detail.path("stops").get(0).path("id").asLong();
    String route = "/api/v1/delivery-trips/" + trip + "/stops/" + stop + "/deliver";
    var body =
        Map.of(
            "proofAttachmentId",
            proof,
            "quantities",
            Map.of(Long.toString(orderLine), 1),
            "note",
            "Đã giao đủ");
    String idem = key();
    var first = call(driverToken, "POST", route, body, 200, idem);
    var replay = call(driverToken, "POST", route, body, 200, idem);
    assertEquals(first, replay);
    assertEquals("COMPLETED", first.path("data").path("status").asText());
    assertEquals("DELIVERED", get(npp, "/api/v1/sales-orders/" + order).path("status").asText());
  }

  @Test
  @Order(22)
  void documentsTicketCommentsAndSettingsPersistWithoutScopeLeak() throws Exception {
    npp = login("npp@synera.local");
    other = login("npp-b@synera.local");
    sales = login("sales@synera.local");
    admin = login("admin@synera.local");
    long document =
        post(
                admin,
                "/api/v1/policy-documents",
                Map.of(
                    "nppId",
                    2,
                    "title",
                    "Chính sách thử nghiệm",
                    "body",
                    "Nội dung có kiểm soát phiên bản"))
            .path("id")
            .asLong();
    assertFalse(get(npp, "/api/v1/policy-documents").toString().contains("Chính sách thử nghiệm"));
    post(
        admin,
        "/api/v1/policy-documents/" + document + "/publish",
        Map.of("reason", "Phê duyệt nội dung"));
    post(npp, "/api/v1/policy-documents/" + document + "/acknowledge", Map.of());
    assertTrue(get(npp, "/api/v1/policy-documents").toString().contains("Chính sách thử nghiệm"));
    assertFalse(
        get(other, "/api/v1/policy-documents").toString().contains("Chính sách thử nghiệm"));
    long ticket =
        post(
                npp,
                "/api/v1/tickets",
                Map.of(
                    "nppId",
                    2,
                    "title",
                    "Hỏi về công nợ",
                    "category",
                    "CREDIT",
                    "description",
                    "Đề nghị xác nhận chứng từ"))
            .path("id")
            .asLong();
    assertTrue(get(npp, "/api/v1/tickets/" + ticket).path("assignedTo").asLong() > 0);
    post(npp, "/api/v1/tickets/" + ticket + "/comments", Map.of("body", "Đã bổ sung chi tiết"));
    assertEquals(1, get(npp, "/api/v1/tickets/" + ticket).path("comments").size());
    call(other, "GET", "/api/v1/tickets/" + ticket, null, 404, null);
    call(
        admin,
        "POST",
        "/api/v1/admin/parameters/receipt-auto-hours",
        Map.of("value", 96.5, "reason", "Invalid fractional duration"),
        422,
        key());
    post(
        admin,
        "/api/v1/admin/parameters/checkin-radius-metres",
        Map.of("value", 100, "reason", "Xác nhận cấu hình"));
    long root =
        post(
                admin,
                "/api/v1/catalog/references",
                Map.of(
                    "kind",
                    "CATEGORY",
                    "code",
                    "CAT-" + key(),
                    "name",
                    "Nhóm cha",
                    "active",
                    true,
                    "reason",
                    "Tạo nhóm"))
            .path("id")
            .asLong();
    call(
        admin,
        "POST",
        "/api/v1/catalog/references/" + root,
        Map.of(
            "kind",
            "CATEGORY",
            "code",
            "CAT-" + key(),
            "name",
            "Vòng lặp",
            "parentId",
            root,
            "active",
            true,
            "version",
            0,
            "reason",
            "Invalid cycle"),
        422,
        key());
  }

  @Test
  @Order(22)
  void asyncReportsExportUnicodePdfAndExcelWithOwnerScope() throws Exception {
    npp = login("npp@synera.local");
    other = login("npp-b@synera.local");
    sales = login("sales@synera.local");
    admin = login("admin@synera.local");
    String from = vn.synera.common.Dates.today().minusDays(1).toString(),
        to = vn.synera.common.Dates.today().toString();
    long pdf =
        post(
                admin,
                "/api/v1/reports/exports",
                Map.of("type", "SELL_IN", "format", "PDF", "from", from, "to", to))
            .path("id")
            .asLong();
    long xlsx =
        post(
                npp,
                "/api/v1/reports/exports",
                Map.of("type", "SELL_OUT", "format", "XLSX", "from", from, "to", to))
            .path("id")
            .asLong();
    new vn.synera.report.ReportJobs(jdbc, reportExports, systemWork).exports();
    var pdfResult =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/reports/exports/" + pdf + "/download")
                    .header("Authorization", "Bearer " + admin))
            .andReturn();
    assertEquals(200, pdfResult.getResponse().getStatus());
    byte[] bytes = pdfResult.getResponse().getContentAsByteArray();
    assertTrue(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"));
    try (var parsed = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      assertTrue(
          new org.apache.pdfbox.text.PDFTextStripper()
              .getText(parsed)
              .contains("Báo cáo tạm tính"));
    }
    var sheet =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/reports/exports/" + xlsx + "/download")
                    .header("Authorization", "Bearer " + npp))
            .andReturn();
    assertEquals(200, sheet.getResponse().getStatus());
    try (var workbook =
        new org.apache.poi.xssf.usermodel.XSSFWorkbook(
            new java.io.ByteArrayInputStream(sheet.getResponse().getContentAsByteArray()))) {
      assertTrue(workbook.getSheetAt(0).getLastRowNum() > 1);
    }
    call(other, "GET", "/api/v1/reports/exports/" + xlsx + "/download", null, 404, null);
    String expiredObject =
        jdbc.queryForObject("select object_key from report_export where id=?", String.class, pdf);
    jdbc.update(
        "update report_export set expires_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        pdf);
    assertTrue(reportExports.cleanupExpired() > 0);
    assertFalse(
        java.nio.file.Files.exists(java.nio.file.Path.of(".data/exports").resolve(expiredObject)));
    call(admin, "GET", "/api/v1/reports/exports/" + pdf + "/download", null, 422, null);
    long schedule =
        post(admin, "/api/v1/reports/schedules", Map.of("type", "SELL_IN", "cadence", "DAILY"))
            .path("id")
            .asLong();
    jdbc.update(
        "update report_schedule set next_run=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        schedule);
    long before = jdbc.queryForObject("select count(*) from report_export", Long.class);
    var jobs = new vn.synera.report.ReportJobs(jdbc, reportExports, systemWork);
    jobs.schedules();
    jobs.schedules();
    assertEquals(before + 1, jdbc.queryForObject("select count(*) from report_export", Long.class));
    call(
        sales,
        "POST",
        "/api/v1/reports/exports",
        Map.of("type", "SELL_IN", "format", "XLSX", "from", from, "to", to),
        422,
        key());
  }

  @Test
  @Order(2)
  void tenantScopeProtectsIdsAndLists() throws Exception {
    call(other, "GET", "/api/v1/stores/1", null, 404, null);
    assertEquals(0, get(other, "/api/v1/stores").size());
    call(sales, "GET", "/api/v1/credit", null, 403, null);
    call(sales, "GET", "/api/v1/sell-in-orders", null, 403, null);
  }

  @Test
  @Order(3)
  void missingApprovalPermissionFailsBeforeDomainLookup() throws Exception {
    call(
        sales,
        "POST",
        "/api/v1/sell-in-orders/999/approve",
        Map.of("reason", "forged"),
        403,
        key());
  }

  @Test
  @Order(4)
  void idempotencyReplaysAndRejectsDifferentPayload() throws Exception {
    String key = key();
    var body = Map.of("nppId", 2, "lines", List.of(line(2)));
    var first = call(npp, "POST", "/api/v1/sell-in-orders", body, 200, key);
    var again = call(npp, "POST", "/api/v1/sell-in-orders", body, 200, key);
    assertEquals(first.path("data").path("id"), again.path("data").path("id"));
    call(
        npp,
        "POST",
        "/api/v1/sell-in-orders",
        Map.of("nppId", 2, "lines", List.of(line(3))),
        409,
        key);
  }

  @Test
  @Order(5)
  void requiresIdempotencyAndValidUnits() throws Exception {
    call(
        npp,
        "POST",
        "/api/v1/sell-in-orders",
        Map.of("nppId", 2, "lines", List.of(line(1))),
        400,
        null);
    call(
        npp,
        "POST",
        "/api/v1/sell-in-orders",
        Map.of("nppId", 2, "lines", List.of(Map.of("skuId", 1, "uom", "UNKNOWN", "qty", 2))),
        422,
        key());
  }

  @Test
  @Order(6)
  void fullOrderStockAndCreditFlow() throws Exception {
    var d = draft(npp, 5);
    fullOrder = d.path("id").asLong();
    var sent = post(npp, "/api/v1/sell-in-orders/" + fullOrder + "/submit", Map.of());
    assertEquals("PENDING_APPROVAL", sent.path("status").asText());
    post(admin, "/api/v1/sell-in-orders/" + fullOrder + "/approve", Map.of());
    long line = sent.path("lines").get(0).path("id").asLong();
    post(
        admin,
        "/api/v1/sell-in-orders/" + fullOrder + "/confirm",
        Map.of("quantities", Map.of(String.valueOf(line), 5)));
    post(admin, "/api/v1/sell-in-orders/" + fullOrder + "/ship", Map.of());
    invoiceAmount = new BigDecimal("1440000.00");
    assertEquals(0, invoiceAmount.compareTo(currentCredit().path("balance").decimalValue()));
    post(admin, "/api/v1/sell-in-orders/" + fullOrder + "/dispatch", Map.of());
    post(admin, "/api/v1/sell-in-orders/" + fullOrder + "/delivered", Map.of());
    var received =
        post(
            npp,
            "/api/v1/sell-in-orders/" + fullOrder + "/receipt",
            Map.of(
                "lines",
                List.of(Map.of("lineId", line, "received", 5, "damaged", 0, "returned", 0))));
    assertEquals("RECEIVED", received.path("status").asText());
    assertEquals(5, received.path("lines").get(0).path("qtyOrdered").intValue());
    assertEquals(5, received.path("lines").get(0).path("qtyShipped").intValue());
    assertEquals(5, received.path("lines").get(0).path("qtyReceived").intValue());
    call(other, "GET", "/api/v1/sell-in-orders/" + fullOrder, null, 404, null);
    call(
        npp,
        "POST",
        "/api/v1/sell-in-orders/" + fullOrder + "/receipt",
        Map.of(
            "lines", List.of(Map.of("lineId", line, "received", 5, "damaged", 0, "returned", 0))),
        422,
        key());
  }

  @Test
  @Order(7)
  void holdAndExceptionAcceptOptionalReason() throws Exception {
    setLimit(BigDecimal.ONE);
    var d = draft(npp, 1);
    long id = d.path("id").asLong();
    assertEquals(
        "ON_HOLD_CREDIT",
        post(npp, "/api/v1/sell-in-orders/" + id + "/submit", Map.of()).path("status").asText());
    post(admin, "/api/v1/sell-in-orders/" + id + "/approve", Map.of());
    post(
        admin,
        "/api/v1/sell-in-orders/" + id + "/cancel",
        Map.of("reason", "Test cleanup cancellation"));
    setLimit(new BigDecimal("500000000"));
  }

  @Test
  @Order(8)
  void paymentIsBoundedAndIdempotent() throws Exception {
    call(
        admin,
        "POST",
        "/api/v1/credit/payments",
        Map.of("nppId", 2, "amount", 999999999, "reference", key()),
        422,
        key());
    String key = key();
    var body = Map.of("nppId", 2, "amount", 1000, "reference", "PAY-" + key);
    call(admin, "POST", "/api/v1/credit/payments", body, 200, key);
    call(admin, "POST", "/api/v1/credit/payments", body, 200, key);
    assertEquals(
        0, new BigDecimal("1439000.00").compareTo(currentCredit().path("balance").decimalValue()));
  }

  @Test
  @Order(9)
  void offlineBatchIsPerItemAndDetectsPriceChange() throws Exception {
    var valid =
        Map.of(
            "clientId",
            key(),
            "idempotencyKey",
            key(),
            "storeId",
            1,
            "createdAt",
            Instant.now().toString(),
            "lines",
            List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 2, "clientPrice", 288000)));
    offlinePayload = valid;
    var malformed = new HashMap<String, Object>(valid);
    malformed.put("clientId", key());
    malformed.put("idempotencyKey", key());
    malformed.put(
        "lines", List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", -1, "clientPrice", 288000)));
    var changed =
        Map.of(
            "clientId",
            key(),
            "idempotencyKey",
            key(),
            "storeId",
            1,
            "createdAt",
            Instant.now().toString(),
            "lines",
            List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 2, "clientPrice", 1)));
    var invalid =
        Map.of(
            "clientId",
            key(),
            "idempotencyKey",
            key(),
            "storeId",
            999,
            "createdAt",
            Instant.now().toString(),
            "lines",
            List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 2, "clientPrice", 288000)));
    var result =
        post(sales, "/api/v1/sales-orders/sync", List.of(valid, changed, invalid, malformed));
    assertEquals("CREATED", result.get(0).path("status").asText());
    assertEquals("PRICE_CHANGED", result.get(1).path("status").asText());
    assertEquals("REJECTED", result.get(2).path("status").asText());
    assertEquals("REJECTED", result.get(3).path("status").asText());
    assertEquals("VALIDATION_FAILED", result.get(3).path("code").asText());
    assertEquals(
        "DUPLICATE",
        post(sales, "/api/v1/sales-orders/sync", List.of(valid)).get(0).path("status").asText());
    assertEquals(1, get(sales, "/api/v1/sales-orders").size());
  }

  @Test
  @Order(10)
  void concurrentCreditChecksSerializePerNpp() throws Exception {
    String username = "owner-" + key() + "@test.local";
    post(
        admin,
        "/api/v1/admin/users",
        Map.of(
            "username",
            username,
            "name",
            "Second NPP owner",
            "password",
            password,
            "role",
            "NPP_OWNER",
            "nppId",
            2));
    String owner2 = login(username);
    BigDecimal current = currentCredit().path("balance").decimalValue();
    setLimit(current.add(new BigDecimal("3000000")));
    long a = draft(npp, 10).path("id").asLong(), b = draft(owner2, 10).path("id").asLong();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first =
          pool.submit(
              () ->
                  post(npp, "/api/v1/sell-in-orders/" + a + "/submit", Map.of())
                      .path("status")
                      .asText());
      var second =
          pool.submit(
              () ->
                  post(owner2, "/api/v1/sell-in-orders/" + b + "/submit", Map.of())
                      .path("status")
                      .asText());
      var statuses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
      assertEquals(1, statuses.stream().filter("PENDING_APPROVAL"::equals).count());
      assertEquals(1, statuses.stream().filter("ON_HOLD_CREDIT"::equals).count());
    } finally {
      pool.shutdownNow();
    }
    post(
        admin,
        "/api/v1/sell-in-orders/" + a + "/cancel",
        Map.of("reason", "Release test exposure"));
    post(
        admin,
        "/api/v1/sell-in-orders/" + b + "/cancel",
        Map.of("reason", "Release test exposure"));
    setLimit(new BigDecimal("500000000"));
  }

  @Test
  @Order(11)
  void routeGpsAndAppointment() throws Exception {
    var route =
        post(
            admin,
            "/api/v1/routes",
            Map.of(
                "nppId",
                2,
                "repId",
                repId,
                "name",
                "Tuyến kiểm thử",
                "storeIds",
                List.of(1),
                "weekday",
                vn.synera.common.Dates.today().getDayOfWeek().getValue()));
    post(
        admin,
        "/api/v1/routes/" + route.path("id").asLong() + "/plan",
        Map.of("day", vn.synera.common.Dates.today().toString()));
    long visit = get(sales, "/api/v1/visits/today").get(0).path("id").asLong();
    var entered =
        post(sales, "/api/v1/visits/" + visit + "/check-in", Map.of("latitude", 0, "longitude", 0));
    assertTrue(entered.path("outsideRadius").asBoolean());
    post(
        sales,
        "/api/v1/visits/" + visit + "/check-out",
        Map.of("reason", "Đã ghé và ghi nhận kết quả"));
    post(
        sales,
        "/api/v1/appointments",
        Map.of(
            "storeId",
            1,
            "title",
            "Gọi xác nhận",
            "dueAt",
            Instant.now().plusSeconds(7200).toString()));
    assertEquals(1, get(sales, "/api/v1/appointments").size());
  }

  @Test
  @Order(11)
  void returnsPreserveLotExpiryAndPostCreditOnlyOnce() throws Exception {
    var order = get(npp, "/api/v1/sell-in-orders/" + fullOrder);
    long line = order.path("lines").get(0).path("id").asLong();
    BigDecimal before = currentCredit().path("balance").decimalValue();
    String expiry = "";
    for (var lot : get(npp, "/api/v1/inventory/balances?warehouseId=2"))
      if (lot.path("lotNo").asText().startsWith("SI-" + fullOrder + "-"))
        expiry = lot.path("expiryDate").asText();
    assertFalse(expiry.isBlank());
    long id =
        post(
                npp,
                "/api/v1/returns",
                Map.of(
                    "orderId",
                    fullOrder,
                    "quantities",
                    Map.of(String.valueOf(line), 1),
                    "reason",
                    "Return unopened goods"))
            .path("id")
            .asLong();
    call(
        npp,
        "POST",
        "/api/v1/returns/" + id + "/approve",
        Map.of("reason", "Forged approval"),
        403,
        key());
    post(admin, "/api/v1/returns/" + id + "/approve", Map.of("reason", "Inspect and quarantine"));
    String idem = key();
    call(admin, "POST", "/api/v1/returns/" + id + "/receive", null, 200, idem);
    call(admin, "POST", "/api/v1/returns/" + id + "/receive", null, 200, idem);
    assertEquals(
        0,
        before
            .subtract(new BigDecimal("288000"))
            .compareTo(currentCredit().path("balance").decimalValue()));
    var lots = get(admin, "/api/v1/inventory/balances?warehouseId=4");
    assertEquals(1, lots.size());
    assertEquals(24, lots.get(0).path("onHand").intValue());
    assertEquals(expiry, lots.get(0).path("expiryDate").asText());
    long excess =
        post(
                npp,
                "/api/v1/returns",
                Map.of(
                    "orderId",
                    fullOrder,
                    "quantities",
                    Map.of(String.valueOf(line), 5),
                    "reason",
                    "Cumulative excess"))
            .path("id")
            .asLong();
    call(
        admin,
        "POST",
        "/api/v1/returns/" + excess + "/approve",
        Map.of("reason", "Must fail"),
        422,
        key());
  }

  @Test
  @Order(11)
  void stocktakeRejectsChangesAfterCounting() throws Exception {
    var lot = get(npp, "/api/v1/inventory/balances?warehouseId=2").get(0);
    long lotId = lot.path("id").asLong();
    String lotNo = lot.path("lotNo").asText();
    long skuId = lot.path("skuId").asLong();
    long stale =
        post(
                npp,
                "/api/v1/inventory/stocktakes",
                Map.of(
                    "warehouseId",
                    2,
                    "counted",
                    Map.of(String.valueOf(lotId), lot.path("onHand").decimalValue()),
                    "reason",
                    "Counted today"))
            .path("id")
            .asLong();
    post(
        npp,
        "/api/v1/inventory/receipts",
        Map.of(
            "expiryDate",
            vn.synera.common.Dates.today().plusYears(1).toString(),
            "warehouseId",
            2,
            "skuId",
            skuId,
            "lotNo",
            lotNo,
            "qtyBase",
            1,
            "reason",
            "New receipt after count"));
    call(
        admin,
        "POST",
        "/api/v1/inventory/stocktakes/" + stale + "/approve",
        Map.of("reason", "Stale count"),
        409,
        key());
    long fresh =
        post(
                npp,
                "/api/v1/inventory/stocktakes",
                Map.of(
                    "warehouseId",
                    2,
                    "counted",
                    Map.of(String.valueOf(lotId), lot.path("onHand").decimalValue()),
                    "reason",
                    "Recount"))
            .path("id")
            .asLong();
    assertEquals(
        "APPROVED",
        post(
                admin,
                "/api/v1/inventory/stocktakes/" + fresh + "/approve",
                Map.of("reason", "Verified recount"))
            .path("status")
            .asText());
  }

  @Test
  @Order(11)
  void tradeWorkflowUsesDifferentApproversAndFrozenAmount() throws Exception {
    String today = vn.synera.common.Dates.today().toString();
    long budget =
        post(
                admin,
                "/api/v1/trade/budgets",
                Map.of("name", "Workflow test", "amount", 100000000, "from", today, "to", today))
            .path("id")
            .asLong();
    long request =
        post(
                other,
                "/api/v1/trade/requests",
                Map.of(
                    "nppId", 3, "budgetId", budget, "title", "Trade support", "amount", 60000000))
            .path("id")
            .asLong();
    assertEquals(
        "PENDING_APPROVAL",
        post(
                admin,
                "/api/v1/trade/requests/" + request + "/decide",
                Map.of("approvedAmount", 60000000, "reason", "First step"))
            .path("status")
            .asText());
    call(
        admin,
        "POST",
        "/api/v1/trade/requests/" + request + "/decide",
        Map.of("approvedAmount", 60000000, "reason", "Same approver must fail"),
        403,
        key());
    String manager = login("manager-north@synera.local");
    call(
        manager,
        "POST",
        "/api/v1/trade/requests/" + request + "/decide",
        Map.of("approvedAmount", 50000000, "reason", "Changed amount"),
        422,
        key());
    assertEquals(
        "APPROVED",
        post(
                manager,
                "/api/v1/trade/requests/" + request + "/decide",
                Map.of("approvedAmount", 60000000, "reason", "Regional approval"))
            .path("status")
            .asText());
  }

  @Test
  @Order(12)
  void snapshotsRemainUnchangedAndLockedPeriodRejectsWrites() throws Exception {
    LocalDate yesterday = vn.synera.common.Dates.today().minusDays(1);
    long prior =
        post(
                admin,
                "/api/v1/closing/periods",
                Map.of(
                    "type",
                    "DAY",
                    "startDate",
                    yesterday.toString(),
                    "endDate",
                    yesterday.toString()))
            .path("id")
            .asLong();
    post(
        admin,
        "/api/v1/closing/periods/" + prior + "/transition",
        Map.of("status", "PRE_CLOSE", "reason", "Prepare"));
    post(
        admin,
        "/api/v1/closing/periods/" + prior + "/transition",
        Map.of("status", "CLOSED", "reason", "Close"));
    var before = get(admin, "/api/v1/closing/periods/" + prior + "/snapshots");
    post(
        admin,
        "/api/v1/closing/periods/" + prior + "/transition",
        Map.of("status", "LOCKED", "reason", "Lock"));
    long current =
        post(
                admin,
                "/api/v1/closing/periods",
                Map.of(
                    "type",
                    "DAY",
                    "startDate",
                    vn.synera.common.Dates.today().toString(),
                    "endDate",
                    vn.synera.common.Dates.today().toString()))
            .path("id")
            .asLong();
    long adjustment =
        post(
                admin,
                "/api/v1/closing/adjustments",
                Map.of(
                    "originalPeriodId",
                    prior,
                    "nppId",
                    2,
                    "delta",
                    500,
                    "reason",
                    "Current-period correction"))
            .path("id")
            .asLong();
    post(
        admin,
        "/api/v1/closing/adjustments/" + adjustment + "/approve",
        Map.of("postedPeriodId", current));
    assertEquals(before, get(admin, "/api/v1/closing/periods/" + prior + "/snapshots"));
    post(
        admin,
        "/api/v1/closing/periods/" + current + "/transition",
        Map.of("status", "PRE_CLOSE", "reason", "Prepare current"));
    post(
        admin,
        "/api/v1/closing/periods/" + current + "/transition",
        Map.of("status", "CLOSED", "reason", "Close current"));
    call(
        npp,
        "POST",
        "/api/v1/sell-in-orders",
        Map.of("nppId", 2, "lines", List.of(line(1))),
        409,
        key());
  }

  @Test
  @Order(13)
  void unavailableExternalServicesDoNotPretendSuccess() throws Exception {
    assertTrue(
        post(null, "/api/v1/auth/forgot-password", Map.of("username", "nobody@local.test"))
            .path("accepted")
            .asBoolean());
    call(
        npp,
        "POST",
        "/api/v1/attachments/presign",
        Map.of("nppId", 2, "filename", "shelf.png", "contentType", "image/png", "sizeBytes", 100),
        503,
        key());
  }

  @Test
  @Order(14)
  void handoverMovesOwnershipWithoutDeletingHistory() throws Exception {
    int ownOrdersBefore = get(sales, "/api/v1/sales-orders").size();
    String username = "replacement-" + key() + "@test.local";
    long id =
        post(
                admin,
                "/api/v1/admin/users",
                Map.of(
                    "username",
                    username,
                    "name",
                    "Người nhận tuyến",
                    "password",
                    password,
                    "role",
                    "SALES",
                    "nppId",
                    2))
            .path("id")
            .asLong();
    post(
        admin,
        "/api/v1/handovers",
        Map.of("nppId", 2, "fromRepId", repId, "toRepId", id, "reason", "Bàn giao có lưu lịch sử"));
    call(sales, "GET", "/api/v1/stores/1", null, 404, null);
    String replacement = login(username);
    assertEquals(1, get(replacement, "/api/v1/stores/1").path("id").asLong());
    assertEquals(ownOrdersBefore, get(sales, "/api/v1/sales-orders").size());
    assertEquals(
        "DUPLICATE",
        post(sales, "/api/v1/sales-orders/sync", List.of(offlinePayload))
            .get(0)
            .path("status")
            .asText());
  }

  @Test
  @Order(15)
  void refreshRotatesAndLogoutRevokesAccess() throws Exception {
    var data =
        post(
            null,
            "/api/v1/auth/login",
            Map.of("username", "npp-b@synera.local", "password", password));
    String oldRefresh = data.path("refreshToken").asText();
    var rotated = post(null, "/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh));
    call(null, "POST", "/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh), 401, null);
    String access = rotated.path("accessToken").asText();
    post(access, "/api/v1/auth/logout", Map.of());
    call(access, "GET", "/api/v1/auth/me", null, 401, null);
  }

  @Test
  @Order(16)
  void passwordResetIsOneTimeAndRevokesExistingAccess() throws Exception {
    String username = "reset-" + key() + "@test.local";
    post(
        admin,
        "/api/v1/admin/users",
        Map.of(
            "username",
            username,
            "name",
            "Recovery test",
            "password",
            password,
            "role",
            "NPP_OWNER",
            "nppId",
            3));
    String access = login(username);
    String token =
        post(null, "/api/v1/auth/forgot-password", Map.of("username", username))
            .path("localResetToken")
            .asText();
    assertFalse(token.isBlank());
    String newPassword = "NewPassword2026!";
    post(null, "/api/v1/auth/reset-password", Map.of("token", token, "newPassword", newPassword));
    call(access, "GET", "/api/v1/auth/me", null, 401, null);
    call(
        null,
        "POST",
        "/api/v1/auth/reset-password",
        Map.of("token", token, "newPassword", newPassword),
        401,
        null);
    call(
        null,
        "POST",
        "/api/v1/auth/login",
        Map.of("username", username, "password", password),
        401,
        null);
    assertFalse(
        post(null, "/api/v1/auth/login", Map.of("username", username, "password", newPassword))
            .path("accessToken")
            .asText()
            .isBlank());
  }

  @Test
  @Order(11)
  void reconciledOrdersCanCloseWithoutRepostingMoney() throws Exception {
    var before = currentCredit().path("balance");
    call(
        npp,
        "POST",
        "/api/v1/sell-in-orders/" + fullOrder + "/close",
        Map.of("reason", "Forged close"),
        403,
        key());
    post(
        admin,
        "/api/v1/sell-in-orders/" + fullOrder + "/reconcile",
        Map.of("reason", "Matched receipt"));
    assertEquals(
        "CLOSED",
        post(
                admin,
                "/api/v1/sell-in-orders/" + fullOrder + "/close",
                Map.of("reason", "Complete order"))
            .path("status")
            .asText());
    assertEquals(before, currentCredit().path("balance"));
  }

  @Test
  @Order(17)
  void loginLocksOtpAttemptsAndExpiredJwtAreRejected() throws Exception {
    String username = "locked-" + key() + "@test.local";
    long id =
        post(
                admin,
                "/api/v1/admin/users",
                Map.of(
                    "username",
                    username,
                    "name",
                    "Lock test",
                    "password",
                    password,
                    "role",
                    "DRIVER",
                    "nppId",
                    3))
            .path("id")
            .asLong();
    String driver = login(username);
    call(
        driver,
        "POST",
        "/api/v1/inventory/receipts",
        Map.of(
            "expiryDate",
            vn.synera.common.Dates.today().plusYears(1).toString(),
            "warehouseId",
            3,
            "skuId",
            1,
            "lotNo",
            "FORGED",
            "qtyBase",
            1,
            "reason",
            "Driver must not mutate stock"),
        403,
        key());
    for (int i = 0; i < 5; i++)
      call(
          null,
          "POST",
          "/api/v1/auth/login",
          Map.of("username", username, "password", "WrongPassword2026!"),
          401,
          null);
    call(
        null,
        "POST",
        "/api/v1/auth/login",
        Map.of("username", username, "password", password),
        423,
        null);
    var challenge =
        post(
            null,
            "/api/v1/auth/login",
            Map.of("username", "admin@synera.local", "password", password));
    String correct = challenge.path("localOtp").asText(),
        wrong = correct.equals("000000") ? "000001" : "000000";
    for (int i = 0; i < 5; i++)
      call(
          null,
          "POST",
          "/api/v1/auth/otp/verify",
          Map.of("challengeId", challenge.path("challengeId").asLong(), "code", wrong),
          401,
          null);
    call(
        null,
        "POST",
        "/api/v1/auth/otp/verify",
        Map.of("challengeId", challenge.path("challengeId").asLong(), "code", correct),
        401,
        null);
    var claims =
        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
            .issuer("synera")
            .subject(Long.toString(id))
            .issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(Instant.now().minusSeconds(300))
            .claim("v", 0)
            .build();
    String expired =
        jwtEncoder
            .encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                    org.springframework.security.oauth2.jwt.JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();
    call(expired, "GET", "/api/v1/auth/me", null, 401, null);
  }

  @Test
  @Order(18)
  void outboxConsumerDoesNotCreateDuplicateNotifications() {
    var pending =
        jdbc.queryForList(
            "select id,event_id from domain_event_outbox where user_id is not null order by id"
                + " limit 1");
    assertFalse(pending.isEmpty());
    var event = pending.get(0);
    long id = ((Number) event.get("id")).longValue();
    outbox.process(id);
    int once =
        jdbc.queryForObject(
            "select count(*) from notification where event_key=?",
            Integer.class,
            event.get("event_id"));
    assertTrue(once > 0);
    outbox.process(id);
    assertEquals(
        once,
        jdbc.queryForObject(
            "select count(*) from notification where event_key=?",
            Integer.class,
            event.get("event_id")));
    assertEquals(
        "DONE",
        jdbc.queryForObject("select status from domain_event_outbox where id=?", String.class, id));
  }

  @Test
  @Order(19)
  void browserSessionUsesHttpOnlyRotationAndRejectsCrossSiteRequests() throws Exception {
    var loginBody = Map.of("username", "npp-b@synera.local", "password", password);
    var blocked =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/session/login")
                    .header("Origin", "https://attacker.invalid")
                    .header("X-Session-Request", "synera")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(loginBody)))
            .andReturn();
    assertEquals(403, blocked.getResponse().getStatus());
    var loginResult =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/session/login")
                    .header("Origin", "http://127.0.0.1:5173")
                    .header("X-Session-Request", "synera")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(loginBody)))
            .andReturn();
    assertEquals(200, loginResult.getResponse().getStatus());
    var body = json.readTree(loginResult.getResponse().getContentAsByteArray()).path("data");
    assertFalse(body.has("refreshToken"));
    assertFalse(body.path("accessToken").asText().isBlank());
    String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
    assertNotNull(setCookie);
    assertTrue(setCookie.contains("HttpOnly"));
    assertTrue(setCookie.contains("SameSite=Strict"));
    String refresh = setCookie.split(";", 2)[0].split("=", 2)[1];
    var rotated =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/session/refresh")
                    .cookie(new jakarta.servlet.http.Cookie("synera_refresh", refresh))
                    .header("Origin", "http://127.0.0.1:5173")
                    .header("X-Session-Request", "synera"))
            .andReturn();
    assertEquals(200, rotated.getResponse().getStatus());
    assertFalse(
        json.readTree(rotated.getResponse().getContentAsByteArray())
            .path("data")
            .has("refreshToken"));
    var replay =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/session/refresh")
                    .cookie(new jakarta.servlet.http.Cookie("synera_refresh", refresh))
                    .header("Origin", "http://127.0.0.1:5173")
                    .header("X-Session-Request", "synera"))
            .andReturn();
    assertEquals(401, replay.getResponse().getStatus());
    String renewed =
        rotated.getResponse().getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
    var logout =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/session/logout")
                    .cookie(new jakarta.servlet.http.Cookie("synera_refresh", renewed))
                    .header("Origin", "http://127.0.0.1:5173")
                    .header("X-Session-Request", "synera"))
            .andReturn();
    assertEquals(200, logout.getResponse().getStatus());
    assertTrue(logout.getResponse().getHeader("Set-Cookie").contains("Max-Age=0"));
    var afterLogout =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/session/refresh")
                    .cookie(new jakarta.servlet.http.Cookie("synera_refresh", renewed))
                    .header("Origin", "http://127.0.0.1:5173")
                    .header("X-Session-Request", "synera"))
            .andReturn();
    assertEquals(401, afterLogout.getResponse().getStatus());
  }

  @Test
  @Order(20)
  void organizationsStaffAndOfferingsPersistWithScope() throws Exception {
    assertTrue(get(npp, "/api/v1/catalog/offerings?nppId=2").size() > 0);
    call(
        npp,
        "POST",
        "/api/v1/organizations",
        Map.of("code", "FORGED", "name", "Forbidden", "region", "SOUTH", "creditLimit", 1000),
        403,
        key());
    long nppId =
        post(
                admin,
                "/api/v1/organizations",
                Map.of(
                    "code",
                    "TEST-" + key().substring(0, 8),
                    "name",
                    "New distributor",
                    "region",
                    "SOUTH",
                    "creditLimit",
                    1000000))
            .path("id")
            .asLong();
    assertTrue(nppId > 3);
    String username = "staff-" + key() + "@test.local";
    long userId =
        post(
                admin,
                "/api/v1/staff",
                Map.of(
                    "nppId",
                    2,
                    "username",
                    username,
                    "name",
                    "New employee",
                    "password",
                    password,
                    "role",
                    "SALES"))
            .path("id")
            .asLong();
    assertTrue(userId > 0);
    assertEquals(2, get(login(username), "/api/v1/auth/me").path("nppId").asLong());
    call(
        npp,
        "POST",
        "/api/v1/staff",
        Map.of(
            "nppId",
            3,
            "username",
            "bad-" + key() + "@test.local",
            "name",
            "Wrong scope",
            "password",
            password,
            "role",
            "SALES"),
        403,
        key());
    call(
        npp,
        "POST",
        "/api/v1/staff",
        Map.of(
            "nppId",
            2,
            "username",
            "bad-" + key() + "@test.local",
            "name",
            "Escalation",
            "password",
            password,
            "role",
            "SYSTEM_ADMIN"),
        403,
        key());
  }

  @Test
  @Order(21)
  void customRolesAndWorkspacesDoNotUnionPermissionsAcrossNpp() throws Exception {
    String role = "E2E_SALES_" + key().substring(0, 8).toUpperCase(Locale.ROOT);
    post(
        admin,
        "/api/v1/admin/roles",
        Map.of(
            "code",
            role,
            "name",
            "Limited sales",
            "baseRole",
            "SALES",
            "authorities",
            List.of("SALES_CREATE", "CRM_WRITE"),
            "active",
            true,
            "reason",
            "Create restricted role"));
    call(
        admin,
        "POST",
        "/api/v1/admin/roles",
        Map.of(
            "code",
            "BAD_" + key().substring(0, 8).toUpperCase(Locale.ROOT),
            "name",
            "Forbidden escalation",
            "baseRole",
            "SALES",
            "authorities",
            List.of("USER_MANAGE"),
            "active",
            true,
            "reason",
            "Must fail"),
        422,
        key());
    String username = "scope-" + key() + "@test.local";
    long userId =
        post(
                admin,
                "/api/v1/admin/users",
                Map.of(
                    "username",
                    username,
                    "name",
                    "Scoped employee",
                    "password",
                    password,
                    "role",
                    role,
                    "nppId",
                    2))
            .path("id")
            .asLong();
    String limited = login(username);
    call(limited, "GET", "/api/v1/reports/dashboard", null, 403, null);
    String idem = key();
    var storeBody =
        Map.of(
            "nppId",
            2,
            "repId",
            userId,
            "name",
            "Scoped shop " + key(),
            "phone",
            "09" + String.format("%08d", System.nanoTime() % 100000000),
            "address",
            "Local test address");
    long shop =
        call(limited, "POST", "/api/v1/stores", storeBody, 200, idem)
            .path("data")
            .path("id")
            .asLong();
    post(
        admin,
        "/api/v1/admin/users/" + userId + "/workspaces",
        Map.of(
            "roleCode",
            "NPP_OWNER",
            "scopeType",
            "NPP",
            "scopeId",
            "3",
            "remove",
            false,
            "reason",
            "Assign separate distributor workspace"));
    call(limited, "GET", "/api/v1/auth/me", null, 401, null);
    limited = login(username);
    var available = get(limited, "/api/v1/auth/session/workspaces");
    String workspace = "";
    for (var entry : available)
      if (entry.path("nppId").asLong() == 3) workspace = entry.path("key").asText();
    assertFalse(workspace.isBlank());
    var switched =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/auth/session/workspace")
                    .header("Authorization", "Bearer " + limited)
                    .header("Origin", "http://127.0.0.1:5173")
                    .header("X-Session-Request", "synera")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("key", workspace))))
            .andReturn();
    assertEquals(200, switched.getResponse().getStatus());
    String npp3 =
        json.readTree(switched.getResponse().getContentAsByteArray())
            .path("data")
            .path("accessToken")
            .asText();
    assertEquals(3, get(npp3, "/api/v1/auth/me").path("nppId").asLong());
    call(npp3, "GET", "/api/v1/stores/" + shop, null, 404, null);
    var credit = get(npp3, "/api/v1/credit");
    assertEquals(1, credit.size());
    assertEquals(3, credit.get(0).path("nppId").asLong());
    call(npp3, "POST", "/api/v1/stores", storeBody, 409, idem);
    post(
        admin,
        "/api/v1/admin/users/" + userId + "/workspaces",
        Map.of(
            "roleCode",
            "NPP_OWNER",
            "scopeType",
            "NPP",
            "scopeId",
            "3",
            "remove",
            true,
            "reason",
            "Revoke temporary assignment"));
    call(npp3, "GET", "/api/v1/auth/me", null, 401, null);
    String primary = login(username);
    assertEquals(1, get(primary, "/api/v1/auth/session/workspaces").size());
    post(
        admin,
        "/api/v1/admin/roles",
        Map.of(
            "code",
            role,
            "name",
            "Read only employee",
            "baseRole",
            "SALES",
            "authorities",
            List.of(),
            "active",
            true,
            "version",
            0,
            "reason",
            "Remove write grants"));
    call(primary, "POST", "/api/v1/stores", storeBody, 403, key());
  }

  @Test
  @Order(2)
  void reconciliationDetectsDriftWithoutRewritingFinancialHistory() throws Exception {
    long postings = jdbc.queryForObject("select count(*) from credit_ledger", Long.class);
    BigDecimal original =
        jdbc.queryForObject("select balance from credit_limit where npp_id=2", BigDecimal.class);
    try {
      jdbc.update("update credit_limit set balance=balance+1 where npp_id=2");
      systemWork.run(() -> operations.scan());
      var alerts = get(npp, "/api/v1/operations/alerts");
      assertTrue(
          java.util.stream.StreamSupport.stream(alerts.spliterator(), false)
              .anyMatch(a -> a.path("category").asText().equals("CREDIT_LEDGER_DRIFT")));
      assertFalse(
          java.util.stream.StreamSupport.stream(
                  get(other, "/api/v1/operations/alerts").spliterator(), false)
              .anyMatch(a -> a.path("alert_key").asText().equals("CREDIT:2")));
      assertEquals(
          0,
          original
              .add(BigDecimal.ONE)
              .compareTo(
                  jdbc.queryForObject(
                      "select balance from credit_limit where npp_id=2", BigDecimal.class)));
      assertEquals(postings, jdbc.queryForObject("select count(*) from credit_ledger", Long.class));
    } finally {
      jdbc.update("update credit_limit set balance=? where npp_id=2", original);
      systemWork.run(() -> operations.scan());
    }
    assertEquals(
        "RESOLVED",
        jdbc.queryForObject(
            "select status from operational_alert where alert_key='CREDIT:2'", String.class));
  }

  @Test
  @Order(10)
  void groupPricesFollowStoreThenNppPriorityAndInvalidateImmediately() throws Exception {
    String suffix = key().substring(0, 8);
    long group =
        post(
                admin,
                "/api/v1/catalog/references",
                Map.of(
                    "kind",
                    "STORE_GROUP",
                    "code",
                    "PRICE-" + suffix,
                    "name",
                    "Nhóm giá thử nghiệm",
                    "nppId",
                    2,
                    "active",
                    true,
                    "reason",
                    "Kiểm tra phân nhóm giá"))
            .path("id")
            .asLong();
    post(
        npp,
        "/api/v1/catalog/references/" + group + "/members",
        Map.of("memberIds", List.of(1), "reason", "Phân nhóm thử nghiệm"));
    var quote = Map.of("nppId", 2, "storeId", 1, "lines", List.of(line(1)));
    BigDecimal initial =
        call(sales, "POST", "/api/v1/pricing/quote", quote, 200, null)
            .path("data")
            .path("lines")
            .get(0)
            .path("price")
            .decimalValue();
    String from = vn.synera.common.Dates.today().toString(),
        to = vn.synera.common.Dates.today().plusDays(2).toString();
    var price =
        Map.of(
            "nppId",
            2,
            "skuId",
            1,
            "uom",
            "THUNG",
            "price",
            initial.subtract(BigDecimal.TEN),
            "storeGroupId",
            group,
            "validFrom",
            from,
            "validTo",
            to,
            "reason",
            "Giá nhóm cửa hàng");
    long priceId = post(admin, "/api/v1/pricing/prices", price).path("id").asLong();
    try {
      assertEquals(
          0,
          initial
              .subtract(BigDecimal.TEN)
              .compareTo(
                  call(sales, "POST", "/api/v1/pricing/quote", quote, 200, null)
                      .path("data")
                      .path("lines")
                      .get(0)
                      .path("price")
                      .decimalValue()));
      call(other, "POST", "/api/v1/pricing/quote", quote, 404, null);
      call(
          sales,
          "POST",
          "/api/v1/catalog/references/" + group + "/members",
          Map.of("memberIds", List.of(1), "remove", true, "reason", "Không được tự đổi nhóm giá"),
          422,
          key());
      post(
          npp,
          "/api/v1/catalog/references/" + group + "/members",
          Map.of("memberIds", List.of(1), "remove", true, "reason", "Bỏ nhóm thử nghiệm"));
      assertEquals(
          0,
          initial.compareTo(
              call(sales, "POST", "/api/v1/pricing/quote", quote, 200, null)
                  .path("data")
                  .path("lines")
                  .get(0)
                  .path("price")
                  .decimalValue()));
    } finally {
      // Test fixture cleanup affects only the isolated database, never production price history.
      jdbc.update("delete from price_item where id=?", priceId);
      jdbc.update(
          "update system_param set param_value=? where param_key='price-cache-version'",
          Long.toString(
              Long.parseLong(
                      jdbc.queryForObject(
                          "select param_value from system_param where"
                              + " param_key='price-cache-version'",
                          String.class))
                  + 1));
    }
  }

  @Test
  @Order(10)
  void transfersPreserveExpiryAndInsufficientStockRollsBackEveryMovement() throws Exception {
    long source = jdbc.queryForObject("select id from warehouse where code='WH-NSX'", Long.class);
    long target = jdbc.queryForObject("select id from warehouse where code='WH-NPP-2'", Long.class);
    LocalDate expiry = vn.synera.common.Dates.today().plusDays(3);
    String lot = "TRANSFER-" + key();
    post(
        admin,
        "/api/v1/inventory/receipts",
        Map.of(
            "warehouseId",
            source,
            "skuId",
            6,
            "lotNo",
            lot,
            "qtyBase",
            7,
            "expiryDate",
            expiry.toString(),
            "reason",
            "Lô nhập có HSD thực"));
    long transfer =
        post(
                admin,
                "/api/v1/inventory/transfers",
                Map.of(
                    "sourceWarehouseId",
                    source,
                    "destinationWarehouseId",
                    target,
                    "skuId",
                    6,
                    "qtyBase",
                    7,
                    "reason",
                    "Điều chuyển FEFO"))
            .path("id")
            .asLong();
    assertEquals(
        expiry,
        jdbc.queryForObject(
                "select expiry_date from stock_balance where warehouse_id=? and lot_no like ?",
                java.sql.Date.class,
                target,
                "TR-" + transfer + "-%")
            .toLocalDate());
    long movements = jdbc.queryForObject("select count(*) from stock_movement", Long.class);
    call(
        admin,
        "POST",
        "/api/v1/inventory/transfers",
        Map.of(
            "sourceWarehouseId",
            source,
            "destinationWarehouseId",
            target,
            "skuId",
            6,
            "qtyBase",
            999999999,
            "reason",
            "Không đủ tồn"),
        409,
        key());
    assertEquals(movements, jdbc.queryForObject("select count(*) from stock_movement", Long.class));
    call(npp, "GET", "/api/v1/inventory/balances?warehouseId=" + target, null, 404, null);
    call(
        admin,
        "POST",
        "/api/v1/inventory/receipts",
        Map.of(
            "warehouseId",
            source,
            "skuId",
            6,
            "lotNo",
            lot,
            "qtyBase",
            1,
            "expiryDate",
            expiry.plusDays(1).toString(),
            "reason",
            "Không đổi HSD lô đã nhập"),
        422,
        key());
  }
}
