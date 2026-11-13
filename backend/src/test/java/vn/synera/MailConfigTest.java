package vn.synera;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.mail.MailHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import vn.synera.config.MailConfig;

class MailConfigTest {
  final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(MailConfig.class)
          .withConfiguration(AutoConfigurations.of(MailHealthContributorAutoConfiguration.class));

  @Test
  void localOtpDoesNotCreateAnUnconfiguredSmtpHealthCheck() {
    context
        .withPropertyValues("synera.integration.otp-mode=local", "spring.mail.host=")
        .run(
            ctx -> {
              assertThat(ctx).doesNotHaveBean("mailSender");
              assertThat(ctx).doesNotHaveBean("mailHealthContributor");
            });
  }

  @Test
  void disabledDeliveryDoesNotCreateASenderEvenWithStaleSmtpSettings() {
    context
        .withPropertyValues(
            "synera.integration.otp-mode=disabled", "spring.mail.host=unused.invalid")
        .run(ctx -> assertThat(ctx).doesNotHaveBean("mailSender"));
  }

  @Test
  void smtpDeliveryRetainsItsHealthCheckAndBindsTlsAndTimeouts() {
    context
        .withPropertyValues(
            "synera.integration.otp-mode=smtp",
            "spring.mail.host=smtp.example.test",
            "spring.mail.port=587",
            "spring.mail.username=test-user",
            "spring.mail.password=test-only",
            "spring.mail.properties.mail.smtp.starttls.enable=true",
            "spring.mail.properties.mail.smtp.connectiontimeout=5000")
        .run(
            ctx -> {
              var sender = (JavaMailSenderImpl) ctx.getBean("mailSender");
              assertThat(sender.getHost()).isEqualTo("smtp.example.test");
              assertThat(sender.getPort()).isEqualTo(587);
              assertThat(sender.getJavaMailProperties())
                  .containsEntry("mail.smtp.starttls.enable", "true")
                  .containsEntry("mail.smtp.connectiontimeout", "5000");
              assertThat(ctx).hasBean("mailHealthContributor");
            });
  }
}
