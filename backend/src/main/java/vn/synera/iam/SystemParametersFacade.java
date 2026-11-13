package vn.synera.iam;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.common.*;

@Service
public class SystemParametersFacade {
  private final JdbcTemplate jdbc;
  private final ActorFacade actors;
  private final AuditService audit;
  private static final Map<String, List<BigDecimal>> RANGES =
      Map.of(
          "checkin-radius-metres",
          List.of(new BigDecimal("10"), new BigDecimal("2000")),
          "receipt-remind-hours",
          List.of(BigDecimal.ONE, new BigDecimal("240")),
          "receipt-auto-hours",
          List.of(new BigDecimal("2"), new BigDecimal("720")),
          "receipt-auto-enabled",
          List.of(BigDecimal.ZERO, BigDecimal.ONE),
          "credit-warn-ratio",
          List.of(new BigDecimal("0.1"), BigDecimal.ONE));

  public SystemParametersFacade(JdbcTemplate jdbc, ActorFacade actors, AuditService audit) {
    this.jdbc = jdbc;
    this.actors = actors;
    this.audit = audit;
  }

  public BigDecimal decimal(String key, BigDecimal fallback) {
    var values =
        jdbc.queryForList(
            "select param_value from system_param where param_key=?", String.class, key);
    return values.isEmpty() ? fallback : new BigDecimal(values.get(0));
  }

  public List<Map<String, Object>> list() {
    return RANGES.entrySet().stream()
        .map(
            e ->
                Map.<String, Object>of(
                    "key",
                    e.getKey(),
                    "value",
                    decimal(
                        e.getKey(),
                        switch (e.getKey()) {
                          case "checkin-radius-metres" -> new BigDecimal("100");
                          case "receipt-remind-hours" -> new BigDecimal("48");
                          case "receipt-auto-enabled" -> BigDecimal.ZERO;
                          case "receipt-auto-hours" -> new BigDecimal("96");
                          default -> new BigDecimal("0.8");
                        }),
                    "min",
                    e.getValue().get(0),
                    "max",
                    e.getValue().get(1)))
        .toList();
  }

  @Transactional
  public Object save(String key, BigDecimal value, String reason) {
    var bounds = RANGES.get(key);
    BusinessException.require(
        bounds != null
            && value != null
            && value.compareTo(bounds.get(0)) >= 0
            && value.compareTo(bounds.get(1)) <= 0,
        "Tham số hoặc giới hạn không hợp lệ.");
    if (!key.equals("credit-warn-ratio"))
      BusinessException.require(
          value.stripTrailingZeros().scale() <= 0, "Bán kính và số giờ phải là số nguyên.");
    if (key.equals("receipt-remind-hours"))
      BusinessException.require(
          value.compareTo(decimal("receipt-auto-hours", new BigDecimal("96"))) < 0,
          "Nhắc nhận phải trước tự xác nhận.");
    if (key.equals("receipt-auto-hours"))
      BusinessException.require(
          value.compareTo(decimal("receipt-remind-hours", new BigDecimal("48"))) > 0,
          "Tự xác nhận phải sau nhắc nhận.");
    int updated =
        jdbc.update(
            "update system_param set param_value=? where param_key=?", value.toPlainString(), key);
    if (updated == 0)
      jdbc.update(
          "insert into system_param(param_key,param_value) values(?,?)",
          key,
          value.toPlainString());
    audit.add(
        actors.current().id(),
        "SystemParam",
        0L,
        "UPDATE",
        Map.of("key", key, "value", value, "reason", reason));
    return Map.of("key", key, "value", value);
  }
}
