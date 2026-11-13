package vn.synera.report;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.synera.common.*;
import vn.synera.iam.SystemWorkFacade;

@Component
@ConditionalOnProperty(name = "synera.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class ReportJobs {
  private final JdbcTemplate jdbc;
  private final ReportExportFacade exports;
  private final SystemWorkFacade system;

  public ReportJobs(JdbcTemplate jdbc, ReportExportFacade exports, SystemWorkFacade system) {
    this.jdbc = jdbc;
    this.exports = exports;
    this.system = system;
  }

  @Scheduled(fixedDelay = 10000)
  @SchedulerLock(name = "report-exports", lockAtMostFor = "PT30M")
  public void exports() {
    try {
      exports.cleanupExpired();
    } catch (java.io.IOException e) {
      org.slf4j.LoggerFactory.getLogger(ReportJobs.class)
          .warn("Report cleanup failed; expired downloads remain blocked", e);
    }
    jdbc.update(
        "update report_export set status='PENDING',available_at=? where status='PROCESSING' and"
            + " started_at<?",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now().minusSeconds(1800)));
    for (var job :
        jdbc.queryForList(
            "select * from report_export where status='PENDING' and available_at<=? order by id"
                + " limit 5",
            Timestamp.from(Instant.now()))) {
      Long id = SqlRecords.id(job, "id");
      int changed =
          jdbc.update(
              "update report_export set status='PROCESSING',started_at=? where id=? and"
                  + " status='PENDING'",
              Timestamp.from(Instant.now()),
              id);
      if (changed == 0) continue;
      try {
        system.runAs(
            SqlRecords.id(job, "user_id"),
            (String) job.get("workspace_key"),
            () -> {
              try {
                exports.generate(id);
              } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
              }
              return 1;
            });
      } catch (Exception e) {
        int attempts = ((Number) job.get("attempts")).intValue() + 1;
        jdbc.update(
            "update report_export set status=?,attempts=?,available_at=?,error_message=? where"
                + " id=?",
            attempts >= 3 ? "FAILED" : "PENDING",
            attempts,
            Timestamp.from(Instant.now().plusSeconds(60L * attempts)),
            "REPORT_FAILED_CHECK_SCOPE_STORAGE_SMTP",
            id);
      }
    }
  }

  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(name = "report-schedules", lockAtMostFor = "PT2M")
  public void schedules() {
    for (var schedule :
        jdbc.queryForList(
            "select * from report_schedule where active=true and next_run<=? order by id limit 100",
            Timestamp.from(Instant.now()))) {
      Long id = SqlRecords.id(schedule, "id");
      try {
        system.runAs(
            SqlRecords.id(schedule, "user_id"),
            (String) schedule.get("workspace_key"),
            () -> {
              exports.fireSchedule(id);
              return 1;
            });
      } catch (Exception e) {
        jdbc.update(
            "update report_schedule set active=false,last_error=? where id=?",
            "SCHEDULE_DISABLED_CHECK_OWNER_SCOPE",
            id);
      }
    }
  }
}
