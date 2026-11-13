package vn.synera.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import java.time.Clock;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class InfrastructureConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  LockProvider lockProvider(JdbcTemplate jdbc) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(jdbc)
            .usingDbTime()
            .build());
  }

  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SYNERA Backend API")
                .version("v1")
                .description(
                    "Modular monolith. Local profile uses persistent H2; production requires"
                        + " MySQL/Redis/MinIO. External providers are never reported as delivered"
                        + " without configuration."))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
