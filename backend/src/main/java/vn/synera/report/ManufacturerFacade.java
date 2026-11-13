package vn.synera.report;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.crm.CrmFacade;
import vn.synera.iam.ActorFacade;
import vn.synera.inventory.InventoryFacade;
import vn.synera.route.RouteFacade;
import vn.synera.salesorder.ReplacementFacade;
import vn.synera.salesorder.SalesFacade;
import vn.synera.sellin.SellInFacade;

/** Read model composed from scoped Facades. No separate ledger or client-side authority. */
@Service
@Transactional(readOnly = true)
public class ManufacturerFacade {
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final InventoryFacade inventory;
  private final RouteFacade routes;
  private final SalesFacade sales;
  private final SellInFacade sellin;
  private final CrmFacade crm;
  private final ReplacementFacade replacements;

  public ManufacturerFacade(
      CatalogFacade catalog,
      ActorFacade actors,
      InventoryFacade inventory,
      RouteFacade routes,
      SalesFacade sales,
      SellInFacade sellin,
      CrmFacade crm,
      ReplacementFacade replacements) {
    this.catalog = catalog;
    this.actors = actors;
    this.inventory = inventory;
    this.routes = routes;
    this.sales = sales;
    this.sellin = sellin;
    this.crm = crm;
    this.replacements = replacements;
  }

  public Map<String, Object> network(LocalDate from, LocalDate to) {
    var context = catalog.manufacturerContext();
    actors.require("REPORT_READ");
    BusinessException.require(
        from != null && to != null && !to.isBefore(from) && !to.isAfter(from.plusDays(366)),
        "Khoảng thời gian tối đa 366 ngày.");
    var npps = catalog.npps();
    var products = catalog.products("");
    var staff = new ArrayList<Map<String, Object>>();
    for (var npp : npps)
      for (var person : actors.staff(npp.id()))
        staff.add(
            Map.of(
                "id",
                person.id(),
                "name",
                person.name(),
                "role",
                person.role(),
                "nppId",
                npp.id()));
    var stock = new ArrayList<Map<String, Object>>();
    for (var wh : catalog.warehouses()) {
      if (npps.stream().noneMatch(n -> n.id().equals(wh.orgId())) || !wh.type().equals("NPP"))
        continue;
      for (var row : inventory.balances(wh.id())) {
        var item = new LinkedHashMap<String, Object>(row);
        item.put("nppId", wh.orgId());
        item.put("warehouseName", wh.name());
        var sku =
            products.stream()
                .filter(p -> p.id().equals(row.get("skuId")))
                .findFirst()
                .orElseThrow();
        item.put("skuName", sku.name());
        item.put("skuCode", sku.code());
        item.put("baseUnit", sku.baseUnit());
        var expiry = Objects.toString(row.get("expiryDate"), "");
        if (!expiry.isBlank() && LocalDate.parse(expiry).isBefore(Dates.today()))
          item.put("available", BigDecimal.ZERO);
        stock.add(item);
      }
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("manufacturer", context);
    result.put("generatedAt", Instant.now());
    result.put("from", from);
    result.put("to", to);
    result.put("layer", "OPERATIONAL");
    result.put("npps", npps);
    result.put("stores", catalog.stores(""));
    result.put("staff", staff);
    result.put("organizations", catalog.organizationTree());
    result.put("routes", routes.networkSchedule());
    result.put("visits", routes.networkVisits(from, to));
    result.put("stock", stock);
    var inbound = sellin.list(null);
    result.put("sellIn", inbound.stream().filter(o -> inPeriod(o, from, to)).toList());
    result.put(
        "pendingSellIn",
        inbound.stream()
            .filter(o -> Set.of("PENDING_APPROVAL", "ON_HOLD_CREDIT").contains(o.get("status")))
            .toList());
    var outbound = sales.list(null);
    result.put("sellOut", outbound.stream().filter(o -> inPeriod(o, from, to)).toList());
    result.put(
        "openSalesOrders",
        outbound.stream()
            .filter(o -> Boolean.TRUE.equals(o.get("hasOutstandingShortage")))
            .toList());
    result.put("appointments", crm.appointments(null));
    result.put("replacements", replacements.list(null));
    var peopleIds = new LinkedHashSet<Long>();
    for (var source : List.of("visits", "sellOut", "appointments", "replacements")) {
      for (var row : (List<?>) result.get(source))
        if (row instanceof Map<?, ?> r && r.get("repId") instanceof Number number)
          peopleIds.add(number.longValue());
    }
    result.put(
        "people",
        actors.referencedDisplayNames(peopleIds).entrySet().stream()
            .map(e -> Map.of("id", e.getKey(), "name", e.getValue()))
            .toList());
    return result;
  }

  public Map<String, Object> nppProfile(Long id, LocalDate from, LocalDate to) {
    catalog.manufacturerContext();
    catalog.npp(id);
    var data = network(from, to);
    var result = new LinkedHashMap<String, Object>();
    result.put("manufacturer", data.get("manufacturer"));
    result.put("generatedAt", data.get("generatedAt"));
    result.put("from", from);
    result.put("to", to);
    result.put("npp", catalog.npp(id));
    result.put("stores", catalog.stores("").stream().filter(s -> s.nppId().equals(id)).toList());
    for (var key :
        List.of(
            "staff",
            "stock",
            "routes",
            "visits",
            "sellIn",
            "sellOut",
            "replacements",
            "openSalesOrders")) {
      @SuppressWarnings("unchecked")
      var rows = (List<Map<String, Object>>) data.get(key);
      result.put(key, rows.stream().filter(r -> Objects.equals(r.get("nppId"), id)).toList());
    }
    var storeIds =
        catalog.stores("").stream()
            .filter(s -> s.nppId().equals(id))
            .map(CatalogFacade.Store::id)
            .toList();
    result.put(
        "appointments",
        crm.appointments(null).stream()
            .filter(o -> o instanceof Map<?, ?> r && storeIds.contains(r.get("storeId")))
            .toList());
    result.put(
        "receivedSales",
        sales.deliveredInPeriod(id, from, to).stream()
            .map(r -> (BigDecimal) r.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    return result;
  }

  private boolean inPeriod(Map<String, Object> order, LocalDate from, LocalDate to) {
    var day = ((Instant) order.get("createdAt")).atZone(Dates.VN).toLocalDate();
    return !day.isBefore(from) && !day.isAfter(to);
  }
}
