package vn.synera.policy;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Catalogue-bound assistance; never creates orders, guesses prices or applies benefits. */
public final class OrderRequestParser {
  private OrderRequestParser() {}

  public record Result(List<Map<String, Object>> lines, List<String> unresolved, String method) {}

  static String fold(String input) {
    return Normalizer.normalize(input, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private static boolean contains(String input, String term) {
    return !term.isBlank()
        && Pattern.compile("(?<![a-z0-9])" + Pattern.quote(fold(term)) + "(?![a-z0-9])")
            .matcher(input)
            .find();
  }

  public static Result parse(String request, List<Map<String, Object>> offerings) {
    var result = new ArrayList<Map<String, Object>>();
    var unresolved = new ArrayList<String>();
    for (var raw : request.split("[;\\n]+")) {
      var input = fold(raw);
      if (input.isBlank()) continue;
      var skus =
          offerings.stream()
              .filter(
                  p ->
                      contains(input, String.valueOf(p.get("code")))
                          || contains(input, String.valueOf(p.get("name"))))
              .map(p -> p.get("skuId"))
              .distinct()
              .toList();
      if (skus.size() != 1) {
        unresolved.add(raw + ": cần mã hoặc tên chính xác của một mặt hàng.");
        continue;
      }
      var units =
          offerings.stream()
              .filter(p -> p.get("skuId").equals(skus.get(0)))
              .filter(p -> contains(input, String.valueOf(p.get("uom"))))
              .toList();
      if (units.size() != 1) {
        unresolved.add(raw + ": cần đơn vị bán rõ ràng.");
        continue;
      }
      var offering = units.get(0);
      var numericInput =
          input
              .replace(fold(String.valueOf(offering.get("code"))), " ")
              .replace(fold(String.valueOf(offering.get("name"))), " ");
      var matcher =
          Pattern.compile("(?<![a-z0-9.-])\\d+(?:[.,]\\d{1,3})?(?![a-z0-9.,])")
              .matcher(numericInput);
      var values = new ArrayList<BigDecimal>();
      while (matcher.find()) values.add(new BigDecimal(matcher.group().replace(',', '.')));
      if (values.size() != 1 || values.get(0).signum() <= 0) {
        unresolved.add(raw + ": cần một số lượng dương.");
        continue;
      }
      var qty = values.get(0);
      var multiple = new BigDecimal(String.valueOf(offering.get("multiple")));
      if (multiple.signum() <= 0 || qty.remainder(multiple).signum() != 0) {
        unresolved.add(raw + ": số lượng chưa đúng bội số bán.");
        continue;
      }
      result.add(
          Map.of(
              "skuId",
              offering.get("skuId"),
              "uom",
              offering.get("uom"),
              "qty",
              qty,
              "name",
              offering.get("name"),
              "code",
              offering.get("code")));
    }
    return new Result(result, unresolved, "CATALOGUE_RULES");
  }
}
