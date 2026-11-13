package vn.synera.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class Jsons {
  private final ObjectMapper mapper;

  public Jsons(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String write(Object o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize application data", e);
    }
  }

  public Object read(String s) {
    try {
      return mapper.readValue(s, Object.class);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored JSON", e);
    }
  }

  public String hash(Object o) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(
                      write(canonical(mapper.valueToTree(o)))
                          .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Object canonical(com.fasterxml.jackson.databind.JsonNode node) {
    if (node.isObject()) {
      var sorted = new java.util.TreeMap<String, Object>();
      node.fields().forEachRemaining(e -> sorted.put(e.getKey(), canonical(e.getValue())));
      return sorted;
    }
    if (node.isArray()) {
      var values = new java.util.ArrayList<Object>();
      node.forEach(value -> values.add(canonical(value)));
      return values;
    }
    if (node.isNumber()) return node.decimalValue().stripTrailingZeros();
    if (node.isNull()) return null;
    if (node.isBoolean()) return node.booleanValue();
    return node.textValue();
  }
}
