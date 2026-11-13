package vn.synera.closing;

import java.sql.Timestamp;
import java.time.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.synera.common.Dates;
import vn.synera.iam.SystemWorkFacade;
import vn.synera.sellin.SellInFacade;

@Component
@ConditionalOnProperty(name = "synera.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class ClosingJobs {
  private final ClosingService closing;
  private final SellInFacade orders;
  private final SystemWorkFacade system;
  private final JdbcTemplate jdbc;
  private final OperationalFacade operations;

  public ClosingJobs(
      ClosingService closing,
      SellInFacade orders,
      SystemWorkFacade system,
      JdbcTemplate jdbc,
      OperationalFacade operations) {
    this.closing = closing;
    this.orders = orders;
    this.system = system;
    this.jdbc = jdbc;
    this.operations = operations;
  }

  @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "nightly-reconcile", lockAtMostFor = "PT2H")
  public void reconcile() {
    run("ledger-stock-reconcile", operations::scan);
    run(
        "nightly-reconcile",
        () -> system.run(() -> orders.reconcileMatched(Dates.today().minusDays(1))));
  }

  @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "daily-close", lockAtMostFor = "PT25M")
  public void daily() {
    run(
        "daily-close",
        () ->
            system.run(
                () -> {
                  closing.closeSystem(
                      "DAY", Dates.today().minusDays(1), Dates.today().minusDays(1));
                  return 1;
                }));
  }

  @Scheduled(cron = "0 10 5 * * MON", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "weekly-close", lockAtMostFor = "PT30M")
  public void weekly() {
    run(
        "weekly-close",
        () ->
            system.run(
                () -> {
                  LocalDate end = Dates.today().minusDays(1);
                  closing.closeSystem("WEEK", end.minusDays(6), end);
                  return 1;
                }));
  }

  @Scheduled(cron = "0 0 5 1-10 * *", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "monthly-close", lockAtMostFor = "PT30M")
  public void monthly() {
    LocalDate today = Dates.today();
    int business = 0;
    for (LocalDate d = today.withDayOfMonth(1); !d.isAfter(today); d = d.plusDays(1))
      if (d.getDayOfWeek() != DayOfWeek.SATURDAY
          && d.getDayOfWeek() != DayOfWeek.SUNDAY
          && jdbc.queryForObject(
                  "select count(*) from business_holiday where holiday_date=?",
                  Integer.class,
                  java.sql.Date.valueOf(d))
              == 0) business++;
    if (business != 3
        || today.getDayOfWeek() == DayOfWeek.SATURDAY
        || today.getDayOfWeek() == DayOfWeek.SUNDAY
        || jdbc.queryForObject(
                "select count(*) from business_holiday where holiday_date=?",
                Integer.class,
                java.sql.Date.valueOf(today))
            != 0) return;
    run(
        "monthly-close",
        () ->
            system.run(
                () -> {
                  LocalDate end = today.withDayOfMonth(1).minusDays(1);
                  closing.closeSystem("MONTH", end.withDayOfMonth(1), end);
                  if (today.getMonthValue() % 3 == 1)
                    closing.closeSystem("QUARTER", end.minusMonths(2).withDayOfMonth(1), end);
                  return 1;
                }));
  }

  @Scheduled(cron = "0 0 * * * *", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "auto-receipt", lockAtMostFor = "PT30M")
  public void receipt() {
    run("auto-receipt", () -> system.run(orders::autoReceipts));
  }

  private void run(String name, java.util.function.IntSupplier action) {
    var start = Timestamp.from(Instant.now());
    try {
      int count = action.getAsInt();
      jdbc.update(
          "insert into job_run_log(name,started_at,finished_at,record_count) values(?,?,?,?)",
          name,
          start,
          Timestamp.from(Instant.now()),
          count);
    } catch (Exception e) {
      jdbc.update(
          "insert into job_run_log(name,started_at,finished_at,error_message) values(?,?,?,?)",
          name,
          start,
          Timestamp.from(Instant.now()),
          e.getClass().getSimpleName());
    }
  }
}
