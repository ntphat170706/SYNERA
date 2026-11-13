package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:manufacturer_scope;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManufacturerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  String login(String user) throws Exception {
    var r =
        json.readTree(
                mvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                json.writeValueAsBytes(
                                    Map.of("username", user, "password", "TestPassword2026!"))))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray())
            .path("data");
    if (r.path("requiresOtp").asBoolean())
      r =
          json.readTree(
                  mvc.perform(
                          MockMvcRequestBuilders.post("/api/v1/auth/otp/verify")
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  json.writeValueAsBytes(
                                      Map.of(
                                          "challengeId",
                                          r.path("challengeId").asLong(),
                                          "code",
                                          r.path("localOtp").asText()))))
                      .andReturn()
                      .getResponse()
                      .getContentAsByteArray())
              .path("data");
    assertFalse(r.path("accessToken").asText().isBlank());
    return r.path("accessToken").asText();
  }

  JsonNode get(String token, String path, int expected) throws Exception {
    var r =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1" + path)
                    .header("Authorization", "Bearer " + token))
            .andReturn()
            .getResponse();
    assertEquals(expected, r.getStatus(), r.getContentAsString());
    return json.readTree(r.getContentAsByteArray());
  }

  @Test
  void distributorAndSalesCannotReadManufacturerNetwork() throws Exception {
    for (var user : List.of("npp@synera.local", "sales@synera.local"))
      get(login(user), "/manufacturer/network", 403);
  }

  @Test
  void networkAndProfileRetainRealSourceAndSeparateUnits() throws Exception {
    var token = login("admin@synera.local");
    var data = get(token, "/manufacturer/network", 200).path("data");
    assertEquals("SINGLE_MANUFACTURER", data.path("manufacturer").path("mode").asText());
    assertEquals(2, data.path("npps").size());
    assertTrue(data.path("generatedAt").isTextual());
    var profile = get(token, "/manufacturer/npps/2/360", 200).path("data");
    for (var key : List.of("stores", "stock", "routes", "sellIn", "sellOut", "replacements"))
      for (var row : profile.path(key)) assertEquals(2, row.path("nppId").asInt(), key);
    for (var lot : profile.path("stock")) assertFalse(lot.path("baseUnit").asText().isBlank());
    get(token, "/manufacturer/network?from=2026-10-20&to=2026-10-01", 422);
  }

  @Test
  void managerCannotInspectAnotherRegion() throws Exception {
    var token = login("manager-north@synera.local");
    var data = get(token, "/manufacturer/network", 200).path("data");
    assertEquals(1, data.path("npps").size());
    assertEquals("NORTH", data.path("npps").get(0).path("region").asText());
    for (var row : data.path("stores")) assertEquals(3, row.path("nppId").asInt());
    get(token, "/manufacturer/npps/2/360", 404);
    get(token, "/manufacturer/npps/3/360", 200);
  }

  @Test
  void pendingDecisionRemainsVisibleOutsideSelectedPeriod() throws Exception {
    String npp = login("npp@synera.local"), admin = login("admin@synera.local");
    var created =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/sell-in-orders")
                    .header("Authorization", "Bearer " + npp)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsBytes(
                            Map.of(
                                "nppId",
                                2,
                                "lines",
                                List.of(Map.of("skuId", 1, "uom", "THUNG", "qty", 1))))))
            .andReturn()
            .getResponse();
    assertEquals(200, created.getStatus());
    long id = json.readTree(created.getContentAsByteArray()).path("data").path("id").asLong();
    var sent =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/sell-in-orders/" + id + "/submit")
                    .header("Authorization", "Bearer " + npp)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andReturn()
            .getResponse();
    assertEquals(200, sent.getStatus(), sent.getContentAsString());
    jdbc.update(
        "update sell_in_order set created_at=? where id=?",
        java.sql.Timestamp.valueOf("2020-01-01 00:00:00"),
        id);
    var data = get(admin, "/manufacturer/network?from=2026-11-01&to=2026-11-30", 200).path("data");
    boolean pending = false, inPeriod = false;
    for (var row : data.path("pendingSellIn")) if (row.path("id").asLong() == id) pending = true;
    for (var row : data.path("sellIn")) if (row.path("id").asLong() == id) inPeriod = true;
    assertTrue(pending, "Unresolved decisions cannot disappear behind a report period");
    assertFalse(inPeriod, "Period totals must not include the old order");
  }

  @Test
  @Transactional
  void multipleManufacturersFailClosedUntilOwnershipIsConfigured() throws Exception {
    var token = login("admin@synera.local");
    jdbc.update(
        "insert into organization(created_at,updated_at,version,code,name,type,region)"
            + " values(current_timestamp,current_timestamp,0,'OTHER-NSX','Other"
            + " manufacturer','NSX','ALL')");
    var result = get(token, "/manufacturer/network", 409);
    assertEquals("MANUFACTURER_SCOPE_UNAVAILABLE", result.path("error").path("code").asText());
  }
}
