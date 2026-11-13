package vn.synera.common;

import java.time.*;

public final class Dates {
  public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

  public static LocalDate today() {
    return LocalDate.now(VN);
  }

  public static Instant start(LocalDate day) {
    return day.atStartOfDay(VN).toInstant();
  }

  private Dates() {}
}
