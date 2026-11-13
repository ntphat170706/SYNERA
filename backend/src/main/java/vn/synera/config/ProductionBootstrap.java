package vn.synera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.iam.BootstrapUsers;

@Component
@Profile("prod")
public class ProductionBootstrap implements ApplicationRunner {
  private final CatalogFacade catalog;
  private final BootstrapUsers users;
  private final String email, password, name, mode, origins;
  private final boolean demo;
  private final String aiMode, callbackSecret;

  public ProductionBootstrap(
      CatalogFacade catalog,
      BootstrapUsers users,
      @Value("${ADMIN_EMAIL:}") String email,
      @Value("${ADMIN_PASSWORD:}") String password,
      @Value("${COMPANY_NAME:SYNERA}") String name,
      @Value("${synera.integration.otp-mode}") String mode,
      @Value("${synera.cors-origins}") String origins,
      @Value("${synera.bootstrap.enabled}") boolean demo,
      @Value("${synera.ai.mode:manual}") String aiMode,
      @Value("${synera.integration.callback-secret:}") String callbackSecret) {
    this.catalog = catalog;
    this.users = users;
    this.email = email;
    this.password = password;
    this.name = name;
    this.mode = mode;
    this.origins = origins;
    this.demo = demo;
    this.aiMode = aiMode;
    this.callbackSecret = callbackSecret;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (demo
        || aiMode.equals("mock")
        || aiMode.equals("http") && callbackSecret.length() < 32
        || !mode.equals("smtp")
        || java.util.Arrays.stream(origins.split(",")).anyMatch(o -> !o.startsWith("https://")))
      throw new IllegalStateException(
          "Production requires SMTP OTP, HTTPS origins and demo bootstrap disabled");
    catalog.initializeProduction(name);
    users.initializeProduction(email, password);
  }
}
