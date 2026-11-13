package vn.synera.display;

import java.sql.Timestamp;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.synera.iam.SystemWorkFacade;

@Component
@ConditionalOnProperty(name = "synera.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class AiJobs {
  private final AiFacade ai;
  private final JdbcTemplate jdbc;
  private final SystemWorkFacade system;

  public AiJobs(AiFacade ai, JdbcTemplate jdbc, SystemWorkFacade system) {
    this.ai = ai;
    this.jdbc = jdbc;
    this.system = system;
  }

  @Scheduled(fixedDelay = 5000)
  @SchedulerLock(name = "display-ai", lockAtMostFor = "PT5M")
  public void run() {
    ai.expireCallbacks();
    for (Long id :
        jdbc.queryForList(
            "select id from ai_job where status='PENDING' and available_at<=? order by id limit 10",
            Long.class,
            Timestamp.from(Instant.now())))
      try {
        system.run(
            () -> {
              ai.process(id);
              return 1;
            });
      } catch (Exception e) {
        ai.failed(id);
      }
  }
}
