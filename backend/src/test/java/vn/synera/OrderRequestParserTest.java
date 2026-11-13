package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.synera.policy.OrderRequestParser;

class OrderRequestParserTest {
  List<Map<String, Object>> offerings =
      List.of(
          Map.of(
              "skuId",
              1L,
              "name",
              "Sữa dinh dưỡng",
              "code",
              "MILK-01",
              "uom",
              "THUNG",
              "multiple",
              1),
          Map.of(
              "skuId",
              1L,
              "name",
              "Sữa dinh dưỡng",
              "code",
              "MILK-01",
              "uom",
              "HOP",
              "multiple",
              6),
          Map.of("skuId", 2L, "name", "Trà xanh", "code", "TEA-01", "uom", "THUNG", "multiple", 1));

  @Test
  void usesRealCatalogueAndDoesNotReturnPricesOrSubmitAnything() {
    var r = OrderRequestParser.parse("10 thùng MILK-01; 6 HOP Sữa dinh dưỡng", offerings);
    assertEquals(2, r.lines().size());
    assertTrue(r.unresolved().isEmpty());
    assertFalse(r.lines().get(0).containsKey("price"));
  }

  @Test
  void ambiguousUnknownMissingUnitNegativeAndWrongMultipleStayUnresolved() {
    for (var text :
        List.of(
            "10 thùng MILK-01 TEA-01",
            "10 THUNG UNKNOWN",
            "10 MILK-01",
            "-1 THUNG MILK-01",
            "1 HOP MILK-01")) {
      assertTrue(OrderRequestParser.parse(text, offerings).lines().isEmpty(), text);
      assertFalse(OrderRequestParser.parse(text, offerings).unresolved().isEmpty(), text);
    }
  }
}
