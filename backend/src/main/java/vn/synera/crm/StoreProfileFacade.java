package vn.synera.crm;

import java.util.Map;
import org.springframework.stereotype.Service;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.display.DisplayReadFacade;
import vn.synera.salesorder.SalesFacade;
import vn.synera.ticket.TicketFacade;

@Service
public class StoreProfileFacade {
  private final CatalogFacade catalog;
  private final CrmFacade crm;
  private final SalesFacade sales;
  private final TicketFacade tickets;
  private final DisplayReadFacade display;

  public StoreProfileFacade(
      CatalogFacade catalog,
      CrmFacade crm,
      SalesFacade sales,
      TicketFacade tickets,
      DisplayReadFacade display) {
    this.catalog = catalog;
    this.crm = crm;
    this.sales = sales;
    this.tickets = tickets;
    this.display = display;
  }

  public Object summary(Long id) {
    return Map.of(
        "store",
        catalog.store(id),
        "orders",
        sales.storePage(id, 0, 20).content(),
        "interactions",
        crm.storePage(id, "interactions", 0, 20).content(),
        "appointments",
        crm.storePage(id, "appointments", 0, 20).content());
  }

  public PageSlice<?> section(Long id, String section, int page, int size) {
    catalog.store(id);
    return switch (section) {
      case "orders" -> sales.storePage(id, page, size);
      case "interactions", "appointments" -> crm.storePage(id, section, page, size);
      case "tickets" -> tickets.storePage(id, page, size);
      case "display" -> display.storePage(id, page, size);
      default -> throw BusinessException.invalid("Phần hồ sơ không hợp lệ.");
    };
  }
}
