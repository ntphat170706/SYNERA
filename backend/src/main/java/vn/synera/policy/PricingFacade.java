package vn.synera.policy;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.policy.domain.*;
import vn.synera.policy.repo.*;

@Service
@Transactional
public class PricingFacade {
  public record PricedLine(
      Long skuId,
      String uom,
      BigDecimal qty,
      BigDecimal factor,
      BigDecimal price,
      BigDecimal discount,
      BigDecimal total) {}

  public record Quote(
      List<PricedLine> lines,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total,
      List<PricingEngine.Benefit> benefits) {}

  private final PriceItemRepository prices;
  private final PromotionRepository promotions;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;
  private final AppliedBenefitRepository applied;
  private final vn.synera.notify.WorkflowFacade workflow;
  private final Jsons json;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  private record PriceChoice(
      Long id,
      Long skuId,
      String uom,
      BigDecimal price,
      Long nppId,
      Long nppGroupId,
      Long storeGroupId) {}

  private record PriceScope(
      Long nppId, Long storeId, List<Long> nppGroups, List<Long> storeGroups) {}

  private record CachedPrices(long expires, List<PriceChoice> choices) {}

  private final java.util.concurrent.ConcurrentMap<String, CachedPrices> priceCache =
      new java.util.concurrent.ConcurrentHashMap<>();

  public PricingFacade(
      PriceItemRepository prices,
      PromotionRepository promotions,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit,
      AppliedBenefitRepository applied,
      vn.synera.notify.WorkflowFacade workflow,
      Jsons json,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.prices = prices;
    this.promotions = promotions;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
    this.applied = applied;
    this.workflow = workflow;
    this.json = json;
    this.jdbc = jdbc;
  }

  public Quote quote(
      Long nppId, List<DocumentRequests.Line> lines, LocalDate date, boolean consume) {
    return quote(nppId, null, lines, date, consume);
  }

  public Quote quote(
      Long nppId,
      Long storeId,
      List<DocumentRequests.Line> lines,
      LocalDate date,
      boolean consume) {
    catalog.npp(nppId);
    var scope = scope(nppId, storeId);
    var resolved = resolve(scope, date);
    BusinessException.require(!lines.isEmpty() && lines.size() <= 100, "Đơn cần 1–100 dòng.");
    var seen = new HashSet<String>();
    var items = new ArrayList<PricingEngine.Item>();
    for (var line : lines) {
      BusinessException.require(
          seen.add(line.skuId() + ":" + line.uom()), "Không lặp SKU/đơn vị trong cùng đơn.");
      var u = catalog.unit(line.skuId(), line.uom());
      var qtyBase = catalog.toBase(line.skuId(), line.uom(), line.qty());
      BusinessException.require(
          qtyBase.compareTo(catalog.product(line.skuId()).minOrder()) >= 0,
          "Số lượng nhỏ hơn tối thiểu.");
      var p =
          resolved.stream()
              .filter(x -> x.skuId.equals(line.skuId()) && x.uom.equals(line.uom()))
              .sorted(pricePriority())
              .findFirst()
              .orElseThrow(
                  () -> BusinessException.invalid("Chưa có bảng giá hiệu lực cho SKU/đơn vị."));
      items.add(
          new PricingEngine.Item(line.skuId(), line.uom(), line.qty(), u.factorToBase(), p.price));
    }
    var selected =
        promotions
            .findAll(
                (root, q, cb) ->
                    cb.and(
                        cb.equal(root.get("status"), "ACTIVE"),
                        cb.or(cb.isNull(root.get("nppId")), cb.equal(root.get("nppId"), nppId)),
                        cb.lessThanOrEqualTo(root.get("startDate"), date),
                        cb.greaterThanOrEqualTo(root.get("endDate"), date)))
            .stream()
            .filter(
                p ->
                    (p.nppGroupId == null || scope.nppGroups.contains(p.nppGroupId))
                        && (p.storeGroupId == null || scope.storeGroups.contains(p.storeGroupId)))
            .sorted(Comparator.comparing(x -> x.id))
            .toList();
    if (consume)
      selected =
          selected.stream()
              .map(p -> promotions.lockById(p.id).orElseThrow(BusinessException::missing))
              .toList();
    var candidates =
        selected.stream()
            .map(
                p ->
                    new PricingEngine.Candidate(
                        p.id,
                        p.skuId,
                        p.minQty,
                        p.minAmount,
                        p.percentOff,
                        p.priority,
                        p.exclusionGroup,
                        p.budget.subtract(p.usedBudget),
                        required(p),
                        p.maxUses == null ? Long.MAX_VALUE : p.maxUses - p.usedUses))
            .toList();
    var result = new PricingEngine().price(items, candidates);
    if (consume)
      for (var benefit : result.benefits()) {
        var p =
            selected.stream()
                .filter(x -> x.id.equals(benefit.promotionId()))
                .findFirst()
                .orElseThrow();
        p.usedBudget = p.usedBudget.add(benefit.amount());
        p.usedUses++;
      }
    var priced = new ArrayList<PricedLine>();
    BigDecimal assigned = BigDecimal.ZERO;
    for (int i = 0; i < items.size(); i++) {
      var item = items.get(i);
      var discount = result.lineDiscounts().get(i);
      discount = discount.min(item.subtotal());
      assigned = assigned.add(discount);
      priced.add(
          new PricedLine(
              item.skuId(),
              item.uom(),
              item.qty(),
              item.factor(),
              item.price(),
              discount,
              item.subtotal().subtract(discount)));
    }
    return new Quote(
        List.copyOf(priced),
        result.subtotal(),
        result.discount(),
        result.total(),
        result.benefits());
  }

  public List<Map<String, Object>> offerings(Long nppId) {
    return offerings(nppId, null);
  }

  public List<Map<String, Object>> offerings(Long nppId, Long storeId) {
    catalog.npp(nppId);
    var date = vn.synera.common.Dates.today();
    var seen = new HashSet<String>();
    var result = new ArrayList<Map<String, Object>>();
    for (var p : resolve(scope(nppId, storeId), date).stream().sorted(pricePriority()).toList()) {
      if (!seen.add(p.skuId + ":" + p.uom)) continue;
      var product = catalog.product(p.skuId);
      if (!product.active()) continue;
      var unit = catalog.unit(p.skuId, p.uom);
      result.add(
          Map.ofEntries(
              Map.entry("skuId", p.skuId),
              Map.entry("code", product.code()),
              Map.entry("name", product.name()),
              Map.entry("category", product.category()),
              Map.entry("uom", p.uom),
              Map.entry("factor", unit.factorToBase()),
              Map.entry("multiple", unit.multiple()),
              Map.entry("minOrder", product.minOrder()),
              Map.entry("baseUnit", product.baseUnit()),
              Map.entry("price", p.price)));
    }
    return result;
  }

  public List<Map<String, Object>> prices(Long nppId) {
    if (nppId != null) catalog.npp(nppId);
    var visible = catalog.visibleNppIds();
    var allowed = visibleGroups();
    return prices.findAll().stream()
        .filter(p -> p.nppId == null || visible.contains(p.nppId))
        .filter(p -> nppId == null || p.nppId == null || p.nppId.equals(nppId))
        .filter(p -> groupVisible(p.nppGroupId, p.storeGroupId, allowed))
        .map(
            p ->
                Map.<String, Object>of(
                    "id",
                    p.id,
                    "nppId",
                    Objects.requireNonNullElse(p.nppId, 0L),
                    "skuId",
                    p.skuId,
                    "uom",
                    p.uom,
                    "price",
                    p.price,
                    "validFrom",
                    p.validFrom,
                    "validTo",
                    p.validTo,
                    "nppGroupId",
                    Objects.requireNonNullElse(p.nppGroupId, 0L),
                    "storeGroupId",
                    Objects.requireNonNullElse(p.storeGroupId, 0L)))
        .toList();
  }

  public Object addPrice(
      Long nppId,
      Long skuId,
      String uom,
      BigDecimal amount,
      LocalDate from,
      LocalDate to,
      String reason) {
    return addPrice(nppId, skuId, uom, amount, from, to, reason, null, null);
  }

  public Object addPrice(
      Long nppId,
      Long skuId,
      String uom,
      BigDecimal amount,
      LocalDate from,
      LocalDate to,
      String reason,
      Long nppGroup,
      Long storeGroup) {
    if (nppId != null) catalog.npp(nppId);
    BusinessException.require(actors.current().nsx(), "Chỉ NSX phát hành bảng giá.");
    validateTarget(nppId, nppGroup, storeGroup);
    catalog.unit(skuId, uom);
    BusinessException.require(
        amount != null
            && amount.signum() >= 0
            && amount.stripTrailingZeros().scale() <= 2
            && !to.isBefore(from),
        "Giá, hiệu lực hoặc lý do không hợp lệ.");
    var p = new PriceItem();
    p.nppGroupId = nppGroup;
    p.storeGroupId = storeGroup;
    p.nppId = nppId;
    p.skuId = skuId;
    p.uom = uom;
    p.price = amount;
    p.validFrom = from;
    p.validTo = to;
    prices.saveAndFlush(p);
    var version =
        jdbc.queryForObject(
            "select param_value from system_param where param_key='price-cache-version' for update",
            String.class);
    jdbc.update(
        "update system_param set param_value=? where param_key='price-cache-version'",
        Long.toString(Long.parseLong(version) + 1));
    priceCache.clear();
    audit.add(
        actors.current().id(),
        "PriceItem",
        p.id,
        "PUBLISH",
        Map.of("price", amount, "reason", reason));
    return Map.of("id", p.id);
  }

  public List<Map<String, Object>> policies() {
    var ids = catalog.visibleNppIds();
    var allowed = visibleGroups();
    return promotions
        .findAll((r, q, cb) -> cb.or(cb.isNull(r.get("nppId")), r.get("nppId").in(ids)))
        .stream()
        .filter(p -> actors.current().nsx() || p.status.equals("ACTIVE"))
        .filter(p -> groupVisible(p.nppGroupId, p.storeGroupId, allowed))
        .map(
            p ->
                SqlRecords.mutable(
                    "id",
                    p.id,
                    "name",
                    p.name,
                    "status",
                    p.status,
                    "percentOff",
                    p.percentOff,
                    "budget",
                    p.budget,
                    "usedBudget",
                    p.usedBudget,
                    "startDate",
                    p.startDate,
                    "endDate",
                    p.endDate,
                    "requiredBase",
                    required(p),
                    "maxUses",
                    p.maxUses,
                    "usedUses",
                    p.usedUses,
                    "nppGroupId",
                    p.nppGroupId,
                    "storeGroupId",
                    p.storeGroupId))
        .toList();
  }

  public Object create(
      String name,
      Long nppId,
      Long skuId,
      BigDecimal percent,
      BigDecimal budget,
      BigDecimal minQty,
      BigDecimal minAmount,
      int priority,
      String group,
      LocalDate from,
      LocalDate until) {
    if (nppId != null) catalog.npp(nppId);
    if (skuId != null) catalog.product(skuId);
    validateTarget(nppId, null, null);
    BusinessException.require(
        !until.isBefore(from)
            && percent.signum() >= 0
            && percent.compareTo(new BigDecimal("100")) <= 0
            && budget.signum() >= 0,
        "Ngày, tỷ lệ hoặc ngân sách không hợp lệ.");
    var p = new Promotion();
    p.name = name;
    p.nppId = nppId;
    p.skuId = skuId;
    p.percentOff = percent;
    p.budget = budget;
    p.minQty = minQty;
    p.minAmount = minAmount;
    p.priority = priority;
    p.exclusionGroup = group;
    p.startDate = from;
    p.endDate = until;
    p.status = "DRAFT";
    promotions.saveAndFlush(p);
    audit.add(actors.current().id(), "Promotion", p.id, "CREATE", Map.of("name", name));
    return Map.of("id", p.id, "status", p.status);
  }

  public Object publish(Long id, String reason) {

    var p = promotions.lockById(id).orElseThrow(BusinessException::missing);
    if (p.nppId != null) catalog.npp(p.nppId);
    BusinessException.require(
        Set.of("DRAFT", "PENDING_APPROVAL").contains(p.status),
        "Chỉ phát hành bản nháp/chờ duyệt.");
    if (!workflow.approve("POLICY", id, p.nppId, p.budget, reason)) {
      p.status = "PENDING_APPROVAL";
      return Map.of("id", id, "status", p.status);
    }
    p.status = "ACTIVE";
    audit.add(actors.current().id(), "Promotion", id, "PUBLISH", Map.of("reason", reason));
    return Map.of("id", id, "status", p.status);
  }

  public void recordBenefits(String type, Long id, Quote quote) {
    for (var b : quote.benefits()) {
      var a = new AppliedBenefit();
      a.documentType = type;
      a.documentId = id;
      a.promotionId = b.promotionId();
      a.amount = b.amount();
      applied.save(a);
    }
  }

  /**
   * Called only after the owning order Facade has checked its scope. Amount is the saved benefit.
   */
  public List<Map<String, Object>> appliedBenefits(String type, Long id) {
    return applied.findByDocumentTypeAndDocumentId(type, id).stream()
        .map(
            b ->
                Map.<String, Object>of(
                    "id",
                    b.id,
                    "promotionId",
                    b.promotionId,
                    "name",
                    promotions
                        .findById(b.promotionId)
                        .map(p -> p.name)
                        .orElse("Chương trình ưu đãi"),
                    "amount",
                    b.amount,
                    "released",
                    b.released))
        .toList();
  }

  public void releaseBenefits(String type, Long id) {
    for (var a :
        applied.findByDocumentTypeAndDocumentId(type, id).stream()
            .filter(x -> !x.released)
            .sorted(java.util.Comparator.comparing(x -> x.promotionId))
            .toList()) {
      var p = promotions.lockById(a.promotionId).orElseThrow();
      p.usedBudget = p.usedBudget.subtract(a.amount);
      p.usedUses = Math.max(0, p.usedUses - 1);
      a.released = true;
    }
  }

  public void seed() {
    if (prices.count() > 0) return;
    BigDecimal[] costs = {
      new BigDecimal("12000"),
      new BigDecimal("9000"),
      new BigDecimal("4000"),
      new BigDecimal("11000"),
      new BigDecimal("15000"),
      new BigDecimal("18000")
    };
    for (int i = 1; i <= 6; i++) {
      for (var entry :
          Map.of("CHAI", BigDecimal.ONE, "LOC", new BigDecimal("6"), "THUNG", new BigDecimal("24"))
              .entrySet()) {
        var p = new PriceItem();
        p.skuId = (long) i;
        p.uom = entry.getKey();
        p.price = costs[i - 1].multiply(entry.getValue());
        p.validFrom = LocalDate.of(2020, 1, 1);
        p.validTo = LocalDate.of(2099, 12, 31);
        prices.save(p);
      }
    }
  }

  private void validateTarget(Long npp, Long nppGroup, Long storeGroup) {
    if (npp != null) catalog.npp(npp);
    if (npp == null)
      BusinessException.require(
          actors.current().region() == null, "Bảng giá/chương trình chung cần phạm vi toàn quốc.");
    if (nppGroup != null) catalog.reference(nppGroup, "NPP_GROUP");
    if (storeGroup != null) {
      var group = catalog.reference(storeGroup, "STORE_GROUP");
      BusinessException.require(
          group.get("npp_id") == null
              || Objects.equals(((Number) group.get("npp_id")).longValue(), npp),
          "Nhóm cửa hàng và NPP áp dụng không trùng phạm vi.");
    }
  }

  private PriceScope scope(Long npp, Long store) {
    catalog.npp(npp);
    if (store != null)
      BusinessException.require(catalog.store(store).nppId().equals(npp), "Cửa hàng khác NPP.");
    return new PriceScope(
        npp,
        store,
        catalog.groups("NPP", npp),
        store == null ? List.of() : catalog.groups("STORE", store));
  }

  private Set<Long> visibleGroups() {
    if (actors.current().nsx() && actors.current().region() == null) return Set.of();
    var groups = new HashSet<Long>();
    for (Long npp : catalog.visibleNppIds()) groups.addAll(catalog.groups("NPP", npp));
    for (var store : catalog.stores("")) groups.addAll(catalog.groups("STORE", store.id()));
    return groups;
  }

  private boolean groupVisible(Long npp, Long store, Set<Long> groups) {
    return actors.current().nsx() && actors.current().region() == null
        || (npp == null || groups.contains(npp)) && (store == null || groups.contains(store));
  }

  private Comparator<PriceChoice> pricePriority() {
    return Comparator.<PriceChoice>comparingInt(
            p -> p.storeGroupId != null ? 4 : p.nppId != null ? 3 : p.nppGroupId != null ? 2 : 1)
        .reversed()
        .thenComparing(PriceChoice::id, Comparator.reverseOrder());
  }

  private List<PriceChoice> resolve(PriceScope scope, LocalDate date) {
    String epoch =
        jdbc.queryForObject(
            "select param_value from system_param where param_key='price-cache-version'",
            String.class);
    String key =
        json.hash(
            Arrays.asList(
                scope.nppId,
                scope.storeId,
                scope.nppGroups,
                scope.storeGroups,
                date.toString(),
                epoch));
    long now = System.nanoTime();
    var existing = priceCache.get(key);
    if (existing != null && existing.expires > now) return existing.choices;
    var choices =
        prices
            .findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.or(cb.isNull(r.get("nppId")), cb.equal(r.get("nppId"), scope.nppId)),
                        cb.or(
                            cb.isNull(r.get("nppGroupId")),
                            r.get("nppGroupId").in(scope.nppGroups)),
                        cb.or(
                            cb.isNull(r.get("storeGroupId")),
                            r.get("storeGroupId").in(scope.storeGroups)),
                        cb.lessThanOrEqualTo(r.get("validFrom"), date),
                        cb.greaterThanOrEqualTo(r.get("validTo"), date)))
            .stream()
            .map(
                p ->
                    new PriceChoice(
                        p.id, p.skuId, p.uom, p.price, p.nppId, p.nppGroupId, p.storeGroupId))
            .toList();
    if (priceCache.size() > 2048) priceCache.clear();
    priceCache.put(key, new CachedPrices(now + 30_000_000_000L, choices));
    return choices;
  }

  public Object targetGroups(Long id, Long nppGroup, Long storeGroup, String reason) {
    var p = promotions.lockById(id).orElseThrow(BusinessException::missing);
    BusinessException.require(p.status.equals("DRAFT"), "Chỉ sửa đối tượng trên bản nháp.");
    validateTarget(p.nppId, nppGroup, storeGroup);
    p.nppGroupId = nppGroup;
    p.storeGroupId = storeGroup;
    audit.add(
        actors.current().id(),
        "Promotion",
        id,
        "TARGETS",
        SqlRecords.mutable("nppGroupId", nppGroup, "storeGroupId", storeGroup, "reason", reason));
    return Map.of("id", id);
  }

  private Map<Long, BigDecimal> required(Promotion p) {
    if (p.requiredSkusJson == null) return Map.of();
    var value = (Map<?, ?>) json.read(p.requiredSkusJson);
    var result = new HashMap<Long, BigDecimal>();
    value.forEach((k, v) -> result.put(Long.valueOf(k.toString()), new BigDecimal(v.toString())));
    return result;
  }

  public Object configureConditions(
      Long id, Map<Long, BigDecimal> required, Long maxUses, String reason) {
    var promotion = promotions.lockById(id).orElseThrow(BusinessException::missing);
    if (promotion.nppId != null) catalog.npp(promotion.nppId);
    BusinessException.require(
        promotion.status.equals("DRAFT")
            && required != null
            && required.size() <= 20
            && (maxUses == null || maxUses > 0),
        "Điều kiện chỉ sửa trên bản nháp; giới hạn lượt phải dương.");
    for (var entry : required.entrySet()) {
      catalog.product(entry.getKey());
      BusinessException.require(
          entry.getValue() != null
              && entry.getValue().signum() > 0
              && entry.getValue().stripTrailingZeros().scale() <= 3,
          "Lượng combo phải dương theo đơn vị cơ sở.");
    }
    promotion.requiredSkusJson = json.write(required);
    promotion.maxUses = maxUses;
    audit.add(
        actors.current().id(),
        "Promotion",
        id,
        "CONDITIONS",
        Map.of(
            "requiredBase", required, "maxUses", maxUses == null ? 0 : maxUses, "reason", reason));
    return Map.of("id", id, "status", promotion.status);
  }
}
