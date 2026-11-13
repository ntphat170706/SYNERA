package vn.synera.notify;

import java.sql.*;
import java.time.*;
import java.util.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.synera.route.RouteFacade;

@Component
@ConditionalOnProperty(name = "synera.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobs {
  private final JdbcTemplate jdbc;
  private final OutboxProcessor outbox;
  private final RouteFacade routes;

  public BackgroundJobs(JdbcTemplate jdbc, OutboxProcessor outbox, RouteFacade routes) {
    this.jdbc = jdbc;
    this.outbox = outbox;
    this.routes = routes;
  }

  @Scheduled(fixedDelayString = "${synera.outbox-delay-ms:2000}")
  @SchedulerLock(name = "outbox-worker", lockAtMostFor = "PT1M")
  public void events() {
    for (var row :
        jdbc.queryForList(
            "select id,attempts from domain_event_outbox where status='PENDING' and available_at<=?"
                + " order by id limit 100",
            Timestamp.from(Instant.now()))) {
      Long id = ((Number) row.get("id")).longValue();
      try {
        outbox.process(id);
      } catch (Exception e) {
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        jdbc.update(
            "update domain_event_outbox set attempts=?,status=?,available_at=?,last_error=? where"
                + " id=?",
            attempts,
            attempts >= 5 ? "DEAD" : "PENDING",
            Timestamp.from(Instant.now().plusSeconds(Math.min(3600, 1L << attempts))),
            e.getClass().getSimpleName(),
            id);
      }
    }
  }

  @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "ticket-sla", lockAtMostFor = "PT5M")
  public void sla() {
    jdbc.update(
        "update approval_request set escalated=true,version=version+1,updated_at=? where"
            + " status='PENDING' and due_at<? and escalated=false",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    run(
        "ticket-sla",
        () ->
            jdbc.update(
                "update ticket set escalated=true,version=version+1,updated_at=? where due_at<? and"
                    + " escalated=false and status not in ('RESOLVED','CLOSED','REJECTED')",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())));
  }

  @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "appointment-reminders", lockAtMostFor = "PT50S")
  public void reminders() {
    run("appointment-reminders", outbox::reminders);
  }

  @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Ho_Chi_Minh")
  @SchedulerLock(name = "visit-planner", lockAtMostFor = "PT20M")
  public void visits() {
    run("visit-planner", routes::planTomorrowSystem);
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
