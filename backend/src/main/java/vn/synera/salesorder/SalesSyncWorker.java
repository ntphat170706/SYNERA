package vn.synera.salesorder;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.policy.PricingFacade;
import vn.synera.salesorder.api.SalesController.SyncItem;

@Service
public class SalesSyncWorker {
  private final SalesFacade sales;
  private final PricingFacade pricing;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final IdempotencyService idem;

  public SalesSyncWorker(
      SalesFacade sales,
      PricingFacade pricing,
      CatalogFacade catalog,
      ActorFacade actors,
      IdempotencyService idem) {
    this.sales = sales;
    this.pricing = pricing;
    this.catalog = catalog;
    this.actors = actors;
    this.idem = idem;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Object sync(SyncItem item) {
    actors.lockCurrent();
    var existing = sales.existing(item.idempotencyKey());
    if (existing.isPresent()) {
      catalog.npp(((Number) existing.get().get("nppId")).longValue());
      Object stored =
          idem.execute(
              item.idempotencyKey(),
              "sales.sync",
              item,
              () -> {
                throw new IllegalStateException("Existing order without idempotency record");
              });
      return Map.of(
          "clientId",
          item.clientId(),
          "status",
          "DUPLICATE",
          "orderId",
          ((Map<?, ?>) stored).get("id"));
    }
    var store = catalog.store(item.storeId());
    var request =
        item.lines().stream()
            .map(l -> new DocumentRequests.Line(l.skuId(), l.uom(), l.qty()))
            .toList();
    var quote = pricing.quote(store.nppId(), store.id(), request, Dates.today(), false);
    if (existing.isEmpty()) {
      for (var p : quote.lines()) {
        var client =
            item.lines().stream()
                .filter(l -> l.skuId().equals(p.skuId()) && l.uom().equals(p.uom()))
                .findFirst()
                .orElseThrow();
        if (client.clientPrice().compareTo(p.price()) != 0)
          return Map.of(
              "clientId",
              item.clientId(),
              "status",
              "PRICE_CHANGED",
              "serverPrice",
              quote.lines(),
              "message",
              "Giá đã thay đổi; xác nhận giá mới trước khi gửi lại.");
      }
    }
    Object result =
        idem.execute(
            item.idempotencyKey(),
            "sales.sync",
            item,
            () ->
                sales.create(
                    item.storeId(),
                    item.clientId(),
                    item.idempotencyKey(),
                    item.createdAt(),
                    request));
    var map = (Map<?, ?>) result;
    return Map.of(
        "clientId",
        item.clientId(),
        "status",
        existing.isPresent() ? "DUPLICATE" : "CREATED",
        "orderId",
        map.get("id"));
  }
}
