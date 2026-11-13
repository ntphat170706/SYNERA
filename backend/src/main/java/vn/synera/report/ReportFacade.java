package vn.synera.report;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.ClosingService;
import vn.synera.common.*;
import vn.synera.credit.CreditFacade;
import vn.synera.iam.ActorFacade;
import vn.synera.route.RouteFacade;
import vn.synera.salesorder.SalesFacade;
import vn.synera.sellin.SellInFacade;

@Service
@Transactional(readOnly = true)
public class ReportFacade {
  private final SellInFacade sellin;
  private final SalesFacade sales;
  private final CreditFacade credit;
  private final ClosingService closing;
  private final CatalogFacade catalog;
  private final RouteFacade routes;
  private final ActorFacade actors;

  public ReportFacade(
      SellInFacade sellin,
      SalesFacade sales,
      CreditFacade credit,
      ClosingService closing,
      CatalogFacade catalog,
      RouteFacade routes,
      ActorFacade actors) {
    this.sellin = sellin;
    this.sales = sales;
    this.credit = credit;
    this.closing = closing;
    this.catalog = catalog;
    this.routes = routes;
    this.actors = actors;
  }

  public Object dashboard() {
    var actor = actors.current();
    if (actor.sales()) {
      var own = sales.list(null);
      return Map.of(
          "layer",
          "PROVISIONAL",
          "generatedAt",
          Instant.now(),
          "orders",
          own.size(),
          "orderValue",
          own.stream()
              .filter(o -> !o.get("status").equals("CANCELLED"))
              .map(o -> (BigDecimal) o.get("total"))
              .reduce(BigDecimal.ZERO, BigDecimal::add),
          "pending",
          own.stream()
              .filter(
                  o -> Set.of("SUBMITTED", "NPP_CONFIRMED", "PICKING").contains(o.get("status")))
              .count(),
          "visits",
          routes.ownVisitCount(Dates.today()));
    }
    var orders = sellin.list(null);
    return Map.of(
        "layer",
        "PROVISIONAL",
        "generatedAt",
        Instant.now(),
        "sellInOrderValue",
        orders.stream()
            .filter(o -> !Set.of("CANCELLED", "REJECTED").contains(o.get("status")))
            .map(o -> (BigDecimal) o.get("total"))
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        "orders",
        orders.size(),
        "pending",
        orders.stream()
            .filter(o -> Set.of("PENDING_APPROVAL", "ON_HOLD_CREDIT").contains(o.get("status")))
            .count(),
        "partners",
        catalog.npps().size(),
        "debt",
        credit.accounts().stream()
            .map(CreditFacade.Account::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  public Object comparison(Long period) {
    return Map.of(
        "generatedAt",
        Instant.now(),
        "provisional",
        dashboard(),
        "closed",
        closing.snapshots(period),
        "note",
        "Hai nguồn được trả riêng; chưa dùng số tạm tính để tính thưởng.");
  }
}
