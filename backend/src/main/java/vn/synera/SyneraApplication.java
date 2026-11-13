package vn.synera;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration.class)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SyneraApplication {
  public static void main(String[] args) {
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    if (java.util.Arrays.asList(args).contains("--migrate-only")) {
      vn.synera.config.DatabaseMigration.run();
      return;
    }
    SpringApplication.run(SyneraApplication.class, args);
  }
}
