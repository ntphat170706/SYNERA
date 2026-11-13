package vn.synera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.credit.CreditFacade;
import vn.synera.iam.BootstrapUsers;
import vn.synera.inventory.InventoryFacade;
import vn.synera.policy.PricingFacade;

@Component
@ConditionalOnProperty(name = "synera.bootstrap.enabled", havingValue = "true")
public class BootstrapConfig implements ApplicationRunner {
  private final CatalogFacade catalog;
  private final CreditFacade credit;
  private final InventoryFacade stock;
  private final PricingFacade pricing;
  private final BootstrapUsers users;
  private final String password;
  private final vn.synera.closing.ClosingService closing;

  public BootstrapConfig(
      CatalogFacade catalog,
      CreditFacade credit,
      InventoryFacade stock,
      PricingFacade pricing,
      BootstrapUsers users,
      @Value("${synera.bootstrap.password}") String password,
      vn.synera.closing.ClosingService closing) {
    this.catalog = catalog;
    this.credit = credit;
    this.stock = stock;
    this.pricing = pricing;
    this.users = users;
    this.password = password;
    this.closing = closing;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    catalog.seed();
    var nppA = catalog.nppIdByCode("NPP-001");
    var ids = users.seed(password, nppA, catalog.nppIdByCode("NPP-002"));
    catalog.seedStore(nppA, ids.get("sales@synera.local"));
    credit.seed();
    pricing.seed();
    stock.seed();
    closing.ensureToday();
  }
}
