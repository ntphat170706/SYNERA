package vn.synera.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** SMTP is optional; do not create a sender or mail health check for local/disabled delivery. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "synera.integration.otp-mode", havingValue = "smtp")
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {
  @Bean
  JavaMailSenderImpl mailSender(MailProperties config) {
    var sender = new JavaMailSenderImpl();
    sender.setHost(config.getHost());
    if (config.getPort() != null) sender.setPort(config.getPort());
    sender.setUsername(config.getUsername());
    sender.setPassword(config.getPassword());
    sender.setProtocol(config.getProtocol());
    sender.setDefaultEncoding(config.getDefaultEncoding().name());
    var properties = new Properties();
    properties.putAll(config.getProperties());
    sender.setJavaMailProperties(properties);
    return sender;
  }
}
