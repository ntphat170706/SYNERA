package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.synera.catalog.CatalogFacade;
import vn.synera.config.ProductionBootstrap;
import vn.synera.iam.BootstrapUsers;

@SpringBootTest(
    properties = {
      "synera.bootstrap.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:production-bootstrap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("test")
class ProductionBootstrapTest {
  @Autowired CatalogFacade catalog;
  @Autowired BootstrapUsers users;
  @Autowired JdbcTemplate jdbc;

  @Test
  void productionProvisioningIsMinimalRepeatableAndRejectsDevelopmentModes() {
    var args = new DefaultApplicationArguments(new String[0]);
    var bootstrap =
        new ProductionBootstrap(
            catalog,
            users,
            "owner@example.test",
            "ProductionTestPassword2026!",
            "Production company",
            "smtp",
            "https://synera.example.test",
            false,
            "manual",
            "");
    bootstrap.run(args);
    bootstrap.run(args);
    assertEquals(1, jdbc.queryForObject("select count(*) from organization", Integer.class));
    assertEquals(2, jdbc.queryForObject("select count(*) from warehouse", Integer.class));
    assertEquals(0, jdbc.queryForObject("select count(*) from sku", Integer.class));
    assertEquals(0, jdbc.queryForObject("select count(*) from sell_in_order", Integer.class));
    assertEquals(2, jdbc.queryForObject("select count(*) from app_user", Integer.class));
    assertEquals(
        Boolean.TRUE,
        jdbc.queryForObject(
            "select otp_required from app_user where username='owner@example.test'",
            Boolean.class));
    for (String mode : new String[] {"local", "disabled"})
      assertThrows(
          IllegalStateException.class,
          () ->
              new ProductionBootstrap(
                      catalog,
                      users,
                      "owner@example.test",
                      "ProductionTestPassword2026!",
                      "Company",
                      mode,
                      "https://synera.example.test",
                      false,
                      "manual",
                      "")
                  .run(args));
    assertThrows(
        IllegalStateException.class,
        () ->
            new ProductionBootstrap(
                    catalog,
                    users,
                    "owner@example.test",
                    "ProductionTestPassword2026!",
                    "Company",
                    "smtp",
                    "https://synera.example.test",
                    false,
                    "mock",
                    "")
                .run(args));
  }
}
