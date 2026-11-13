package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.synera.common.Jsons;

class JsonsTest {
  final Jsons json = new Jsons(new ObjectMapper());

  @Test
  void retriesIgnoreObjectPropertyOrderAndNumericScale() {
    var first = new LinkedHashMap<String, Object>();
    first.put("qty", 1);
    first.put("lines", Map.of("b", 2, "a", 1));
    var second = new LinkedHashMap<String, Object>();
    second.put("lines", Map.of("a", 1, "b", 2));
    second.put("qty", new BigDecimal("1.000"));
    assertEquals(json.hash(first), json.hash(second));
  }

  @Test
  void retriesPreserveArrayOrder() {
    assertNotEquals(json.hash(List.of(1, 2)), json.hash(List.of(2, 1)));
  }

  @Test
  void operationIsPartOfRequestIdentity() {
    assertNotEquals(
        json.hash(List.of("create", Map.of("qty", 1))),
        json.hash(List.of("cancel", Map.of("qty", 1))));
  }
}
