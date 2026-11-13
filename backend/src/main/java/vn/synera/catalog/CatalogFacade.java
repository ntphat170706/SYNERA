package vn.synera.catalog;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.domain.*;
import vn.synera.catalog.repo.*;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;

@Service
@Transactional(readOnly = true)
public class CatalogFacade {
  public record Npp(Long id, String code, String name, String region) {}

  public record Product(
      Long id,
      String code,
      String name,
      String category,
      boolean active,
      BigDecimal minOrder,
      String baseUnit) {}

  public record Unit(Long skuId, String code, BigDecimal factorToBase, BigDecimal multiple) {}

  public record Store(
      Long id,
      Long nppId,
      Long repId,
      Long routeId,
      String name,
      String owner,
      String phone,
      String address,
      Double latitude,
      Double longitude,
      String groupName,
      String status) {}

  public record WarehouseInfo(Long id, Long orgId, String name, String type) {}

  private final OrganizationRepository orgs;
  private final SkuRepository skus;
  private final SkuUomRepository uoms;
  private final StoreEntityRepository stores;
  private final WarehouseRepository warehouses;
  private final ActorFacade actors;
  private final AuditService audit;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public CatalogFacade(
      OrganizationRepository orgs,
      SkuRepository skus,
      SkuUomRepository uoms,
      StoreEntityRepository stores,
      WarehouseRepository warehouses,
      ActorFacade actors,
      AuditService audit,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.orgs = orgs;
    this.skus = skus;
    this.uoms = uoms;
    this.stores = stores;
    this.warehouses = warehouses;
    this.actors = actors;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  public List<Long> visibleNppIds() {
    var a = actors.current();
    return orgs
        .findAll(
            (root, q, cb) -> {
              var type = cb.equal(root.get("type"), "NPP");
              if (!a.nsx()) return cb.and(type, cb.equal(root.get("id"), a.nppId()));
              if (a.region() != null && !a.region().isBlank())
                return cb.and(type, cb.equal(root.get("region"), a.region()));
              return type;
            })
        .stream()
        .map(x -> x.id)
        .toList();
  }

  /** This installation has no multi-manufacturer ownership/grant model yet. Never merge brands. */
  public Map<String, Object> manufacturerContext() {
    if (!actors.current().nsx())
      throw new BusinessException(
          "FORBIDDEN_SCOPE", "Chỉ người dùng nhà sản xuất được mở mạng lưới.", 403);
    var manufacturers = orgs.findAll((r, q, cb) -> cb.equal(r.get("type"), "NSX"));
    if (manufacturers.size() != 1)
      throw new BusinessException(
          "MANUFACTURER_SCOPE_UNAVAILABLE",
          "Cần cấu hình phạm vi nhãn hàng trước khi mở báo cáo mạng lưới.",
          409);
    var org = manufacturers.get(0);
    return Map.of(
        "id",
        org.id,
        "code",
        org.code,
        "name",
        org.name,
        "mode",
        "SINGLE_MANUFACTURER",
        "region",
        Objects.toString(actors.current().region(), "ALL"));
  }

  @Transactional
  public Npp createNpp(String code, String name, String region) {
    jdbc.queryForList("select id from organization_graph_guard where id=1 for update");
    BusinessException.require(actors.current().admin(), "Chỉ quản trị hệ thống tạo NPP.");
    BusinessException.require(
        code != null
            && code.matches("[A-Za-z0-9_-]{2,40}")
            && name != null
            && !name.isBlank()
            && region != null
            && !region.isBlank(),
        "Mã, tên và vùng không hợp lệ.");
    BusinessException.require(
        orgs.findAll((r, q, cb) -> cb.equal(r.get("code"), code)).isEmpty(),
        "Mã tổ chức đã tồn tại.");
    var parent =
        orgs.findAll((r, q, cb) -> cb.equal(r.get("type"), "NSX")).stream()
            .findFirst()
            .orElseThrow(BusinessException::missing);
    var org = new Organization();
    org.code = code;
    org.name = name;
    org.region = region;
    org.type = "NPP";
    org.parentId = parent.id;
    orgs.saveAndFlush(org);
    var warehouse = new Warehouse();
    warehouse.orgId = org.id;
    warehouse.code = "WH-" + code;
    warehouse.name = "Kho " + name;
    warehouse.type = "NPP";
    warehouses.saveAndFlush(warehouse);
    audit.add(
        actors.current().id(),
        "Organization",
        org.id,
        "CREATE",
        Map.of("code", code, "region", region));
    return npp(org.id);
  }

  public Npp npp(Long id) {
    var x =
        orgs.findById(id).filter(o -> o.type.equals("NPP")).orElseThrow(BusinessException::missing);
    if (!actors.current().permitsNpp(id, x.region)) throw BusinessException.missing();
    return new Npp(x.id, x.code, x.name, x.region);
  }

  public List<Npp> npps() {
    return visibleNppIds().stream().map(this::npp).toList();
  }

  public Product product(Long id) {
    var x = skus.findById(id).orElseThrow(BusinessException::missing);
    return product(x);
  }

  public List<Product> products(String query) {
    return skus
        .findAll(
            (root, q, cb) -> {
              String s = "%" + query.toLowerCase(Locale.ROOT) + "%";
              return cb.or(
                  cb.like(cb.lower(root.get("code")), s), cb.like(cb.lower(root.get("name")), s));
            })
        .stream()
        .map(this::product)
        .toList();
  }

  private Product product(Sku s) {
    return new Product(s.id, s.code, s.name, s.category, s.active, s.minOrder, s.baseUnit);
  }

  public List<Unit> units(Long skuId) {
    product(skuId);
    return uoms.findBySkuId(skuId).stream()
        .map(u -> new Unit(skuId, u.code, u.factorToBase, u.multiple))
        .toList();
  }

  public Unit unit(Long skuId, String code) {
    var p = product(skuId);
    BusinessException.require(p.active(), "SKU đã ngừng bán.");
    var u =
        uoms.findBySkuIdAndCode(skuId, code)
            .orElseThrow(() -> BusinessException.invalid("Đơn vị không tồn tại cho SKU."));
    return new Unit(skuId, u.code, u.factorToBase, u.multiple);
  }

  public BigDecimal toBase(Long skuId, String code, BigDecimal qty) {
    var u = unit(skuId, code);
    BusinessException.require(
        qty != null && qty.signum() > 0 && qty.remainder(u.multiple()).signum() == 0,
        "Số lượng phải dương và đúng bội số đơn vị.");
    return qty.multiply(u.factorToBase()).setScale(3, RoundingMode.UNNECESSARY);
  }

  public Store store(Long id) {
    var s = stores.findById(id).orElseThrow(BusinessException::missing);
    npp(s.nppId);
    actors.assertOwnRep(s.repId);
    return dto(s);
  }

  public List<Store> stores(String query) {
    var ids = visibleNppIds();
    var a = actors.current();
    return stores
        .findAll(
            (root, q, cb) -> {
              var scope = root.get("nppId").in(ids);
              var text =
                  cb.like(cb.lower(root.get("name")), "%" + query.toLowerCase(Locale.ROOT) + "%");
              return a.sales()
                  ? cb.and(scope, text, cb.equal(root.get("repId"), a.id()))
                  : cb.and(scope, text);
            })
        .stream()
        .map(this::dto)
        .toList();
  }

  private Store dto(StoreEntity s) {
    return new Store(
        s.id,
        s.nppId,
        s.repId,
        s.routeId,
        s.name,
        s.owner,
        s.phone,
        s.address,
        s.latitude,
        s.longitude,
        s.groupName,
        s.status);
  }

  @Transactional
  public Store createStore(
      Long nppId,
      Long repId,
      String name,
      String owner,
      String phone,
      String address,
      Double lat,
      Double lng) {
    npp(nppId);
    actors.assertOwnRep(repId);
    actors.assertRep(repId, nppId);
    BusinessException.require(
        name != null
            && !name.isBlank()
            && name.length() <= 255
            && address != null
            && !address.isBlank()
            && address.length() <= 1000
            && (lat == null) == (lng == null),
        "Tên, địa chỉ hoặc cặp tọa độ không hợp lệ.");
    String normalized = normalizePhone(phone);
    if (stores.findByNppIdAndPhone(nppId, normalized).isPresent())
      throw BusinessException.conflict("DUPLICATE_STORE", "Số điện thoại đã có trong NPP.");
    if (lat != null && lng != null) {
      BusinessException.require(
          lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180, "Tọa độ không hợp lệ.");
      boolean duplicate =
          stores.findByNppId(nppId).stream()
              .anyMatch(
                  s ->
                      s.latitude != null
                          && s.longitude != null
                          && distance(lat, lng, s.latitude, s.longitude) < 30
                          && s.name.trim().equalsIgnoreCase(name.trim()));
      if (duplicate)
        throw BusinessException.conflict(
            "SUSPECTED_DUPLICATE_STORE", "Cửa hàng cùng tên nằm trong bán kính 30m.");
    }
    var s = new StoreEntity();
    s.nppId = nppId;
    s.repId = repId;
    s.name = name.trim();
    s.owner = owner;
    s.phone = normalized;
    s.address = address;
    s.latitude = lat;
    s.longitude = lng;
    s.groupName = "C";
    s.status = "ACTIVE";
    stores.saveAndFlush(s);
    audit.add(actors.current().id(), "Store", s.id, "CREATE", Map.of("nppId", nppId));
    return dto(s);
  }

  public WarehouseInfo warehouse(Long id) {
    var w = warehouses.findById(id).orElseThrow(BusinessException::missing);
    var org = orgs.findById(w.orgId).orElseThrow(BusinessException::missing);
    var a = actors.current();
    if (org.type.equals("NPP")) npp(org.id);
    else if (!a.nsx()) throw BusinessException.missing();
    return new WarehouseInfo(w.id, w.orgId, w.name, w.type);
  }

  @Transactional
  public void lockWarehouses(List<Long> ids) {
    for (Long id : ids.stream().distinct().sorted().toList()) {
      warehouse(id);
      warehouses.lockById(id).orElseThrow(BusinessException::missing);
    }
  }

  public Long quarantineWarehouse() {
    return warehouses.findAll().stream()
        .filter(w -> w.type.equals("QUARANTINE"))
        .map(w -> w.id)
        .findFirst()
        .orElseThrow(() -> BusinessException.invalid("Chưa có kho cách ly hàng hoàn."));
  }

  public Long manufacturerWarehouse() {
    return warehouses.findAll().stream()
        .filter(w -> w.type.equals("NSX"))
        .map(w -> w.id)
        .findFirst()
        .orElseThrow(BusinessException::missing);
  }

  public Long nppWarehouse(Long nppId) {
    npp(nppId);
    return warehouses.findByOrgId(nppId).stream()
        .map(w -> w.id)
        .findFirst()
        .orElseThrow(BusinessException::missing);
  }

  public List<WarehouseInfo> warehouses() {
    var ids = visibleNppIds();
    var a = actors.current();
    return warehouses.findAll().stream()
        .filter(
            w ->
                ids.contains(w.orgId)
                    || a.nsx()
                        && orgs.findById(w.orgId).map(o -> o.type.equals("NSX")).orElse(false))
        .map(w -> new WarehouseInfo(w.id, w.orgId, w.name, w.type))
        .toList();
  }

  @Transactional
  public Object transferRep(Long nppId, Long from, Long to) {
    npp(nppId);
    actors.assertRep(to, nppId);
    int count = 0;
    for (var s : stores.findByNppId(nppId)) {
      if (s.repId.equals(from)) {
        s.repId = to;
        count++;
      }
    }
    return count;
  }

  @Transactional
  public void setRoute(Long storeId, Long routeId) {
    var s = stores.findById(storeId).orElseThrow(BusinessException::missing);
    npp(s.nppId);
    s.routeId = routeId;
  }

  @Transactional
  public Product createProduct(
      String code,
      String name,
      String category,
      String baseUnit,
      BigDecimal minOrder,
      Map<String, BigDecimal> factors) {
    var s = new Sku();
    s.code = code;
    s.name = name;
    s.category = category;
    s.baseUnit = baseUnit;
    s.minOrder = minOrder;
    s.active = true;
    skus.saveAndFlush(s);
    BusinessException.require(
        factors.containsKey(baseUnit) && factors.get(baseUnit).compareTo(BigDecimal.ONE) == 0,
        "Đơn vị cơ sở phải có hệ số 1.");
    for (var e : factors.entrySet()) {
      BusinessException.require(e.getValue().signum() > 0, "Hệ số đơn vị phải dương.");
      var u = new SkuUom();
      u.skuId = s.id;
      u.code = e.getKey();
      u.factorToBase = e.getValue();
      u.multiple = BigDecimal.ONE;
      uoms.save(u);
    }
    return product(s);
  }

  public static String normalizePhone(String phone) {
    String s = phone.replaceAll("[^0-9]", "");
    if (s.startsWith("84")) s = "0" + s.substring(2);
    BusinessException.require(
        s.matches("0[0-9]{9}"), "Số điện thoại phải có 10 chữ số hoặc mã +84 tương ứng.");
    return s;
  }

  public static double distance(double lat1, double lon1, double lat2, double lon2) {
    double a =
        Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
    return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  @Transactional
  public void seed() {
    if (orgs.count() > 0) return;
    var nsx = new Organization();
    nsx.code = "NSX-001";
    nsx.name = "SYNERA Manufacturing";
    nsx.type = "NSX";
    nsx.region = "ALL";
    orgs.saveAndFlush(nsx);
    var wh = new Warehouse();
    wh.orgId = nsx.id;
    wh.code = "WH-NSX";
    wh.name = "Kho trung tâm";
    wh.type = "NSX";
    warehouses.save(wh);
    for (int i = 1; i <= 2; i++) {
      var o = new Organization();
      o.code = "NPP-00" + i;
      o.name = i == 1 ? "Minh Phát" : "Hoàng Gia";
      o.type = "NPP";
      o.parentId = nsx.id;
      o.region = i == 1 ? "SOUTH" : "NORTH";
      orgs.saveAndFlush(o);
      var w = new Warehouse();
      w.orgId = o.id;
      w.code = "WH-NPP-" + i;
      w.name = "Kho " + o.name;
      w.type = "NPP";
      warehouses.save(w);
    }
    var quarantine = new Warehouse();
    quarantine.orgId = nsx.id;
    quarantine.code = "WH-QUARANTINE";
    quarantine.name = "Kho cách ly hàng hoàn";
    quarantine.type = "QUARANTINE";
    warehouses.save(quarantine);
    String[] names = {
      "Sữa tươi nguyên chất",
      "Trà xanh thanh mát",
      "Nước khoáng thiên nhiên",
      "Nước ép cam tươi",
      "Bánh quy yến mạch",
      "Sữa hạt hạnh nhân"
    };
    for (int i = 0; i < names.length; i++) {
      createProduct(
          "SKU-00" + (i + 1),
          names[i],
          i == 4 ? "FOOD" : "DRINK",
          "CHAI",
          BigDecimal.ONE,
          Map.of(
              "CHAI", BigDecimal.ONE, "LOC", new BigDecimal("6"), "THUNG", new BigDecimal("24")));
    }
  }

  @Transactional
  public void initializeProduction(String name) {
    if (orgs.count() != 0) return;
    var org = new Organization();
    org.code = "NSX-001";
    org.name = name;
    org.type = "NSX";
    org.region = "ALL";
    orgs.saveAndFlush(org);
    for (String type : List.of("NSX", "QUARANTINE")) {
      var warehouse = new Warehouse();
      warehouse.orgId = org.id;
      warehouse.code = "WH-" + type;
      warehouse.name = type.equals("NSX") ? "Kho trung tâm" : "Kho cách ly hàng hoàn";
      warehouse.type = type;
      warehouses.saveAndFlush(warehouse);
    }
  }

  @Transactional
  public void seedStore(Long nppId, Long repId) {
    if (stores.count() > 0) return;
    var s = new StoreEntity();
    s.nppId = nppId;
    s.repId = repId;
    s.name = "Tạp hóa Thanh Hương";
    s.owner = "Nguyễn Thanh Hương";
    s.phone = "0901234567";
    s.address = "128 Nguyễn Đình Chiểu, Quận 3, TP.HCM";
    s.latitude = 10.782;
    s.longitude = 106.687;
    s.groupName = "A";
    s.status = "ACTIVE";
    stores.save(s);
  }

  public Long nppIdByCode(String code) {
    return orgs.findByCode(code).orElseThrow(BusinessException::missing).id;
  }

  public String notificationRegion(Long id) {
    return orgs.findById(id).filter(o -> o.type.equals("NPP")).map(o -> o.region).orElse("");
  }

  public List<Long> groups(String type, Long id) {
    if (type.equals("NPP")) npp(id);
    else if (type.equals("STORE")) store(id);
    else throw BusinessException.invalid("Loại thành viên không hợp lệ.");
    return jdbc.queryForList(
        "select m.reference_id from catalog_membership m join catalog_reference r on"
            + " r.id=m.reference_id where m.member_type=? and m.member_id=? and r.active=true order"
            + " by m.reference_id",
        Long.class,
        type,
        id);
  }

  public Map<String, Object> reference(Long id, String kind) {
    var rows =
        jdbc.queryForList(
            "select * from catalog_reference where id=? and kind=? and active=true", id, kind);
    if (rows.isEmpty()) throw BusinessException.missing();
    var row = rows.get(0);
    if (row.get("npp_id") != null) npp(SqlRecords.id(row, "npp_id"));
    return row;
  }

  public List<Map<String, Object>> organizationTree() {
    var visible = new HashSet<>(visibleNppIds());
    for (Long id : List.copyOf(visible)) {
      Long parent = orgs.findById(id).orElseThrow().parentId;
      while (parent != null && visible.add(parent))
        parent = orgs.findById(parent).orElseThrow().parentId;
    }
    return orgs.findAll().stream()
        .filter(o -> actors.current().admin() || visible.contains(o.id))
        .map(
            o ->
                SqlRecords.mutable(
                    "id",
                    o.id,
                    "code",
                    o.code,
                    "name",
                    o.name,
                    "type",
                    o.type,
                    "region",
                    o.region,
                    "parentId",
                    o.parentId,
                    "version",
                    o.version))
        .toList();
  }

  @Transactional
  public Object saveOrganization(
      Long id,
      String code,
      String name,
      String type,
      Long parent,
      String region,
      Long version,
      String reason) {
    BusinessException.require(actors.current().admin(), "Chỉ quản trị hệ thống sửa cây tổ chức.");
    jdbc.queryForList("select id from organization_graph_guard where id=1 for update");
    BusinessException.require(
        name != null
            && !name.isBlank()
            && name.length() <= 255
            && region != null
            && !region.isBlank(),
        "Tên, vùng hoặc lý do không hợp lệ.");
    var entity =
        id == null ? new Organization() : orgs.lockById(id).orElseThrow(BusinessException::missing);
    if (id != null) {
      if (version == null || entity.version.longValue() != version)
        throw BusinessException.conflict("OPTIMISTIC_LOCK", "Tổ chức đã thay đổi.");
      type = entity.type;
    } else
      BusinessException.require(
          Set.of("REGION", "DEPARTMENT").contains(type)
              && code != null
              && code.matches("[A-Za-z0-9_-]{2,40}"),
          "Đơn vị mới chỉ dùng REGION/DEPARTMENT; tạo NPP bằng chức năng riêng.");
    BusinessException.require(
        type.equals("NSX") ? parent == null : parent != null, "Chỉ tổ chức NSX gốc không có cha.");
    var seen = new HashSet<Long>();
    Long next = parent;
    while (next != null) {
      BusinessException.require(
          !Objects.equals(next, id) && seen.add(next), "Cây tổ chức không được có vòng lặp.");
      var ancestor = orgs.findById(next).orElseThrow(BusinessException::missing);
      BusinessException.require(!ancestor.type.equals("NPP"), "Không đặt đơn vị dưới một NPP.");
      next = ancestor.parentId;
    }
    if (id == null) {
      entity.code = code;
      entity.type = type;
    }
    entity.name = name;
    entity.region = region;
    entity.parentId = parent;
    orgs.saveAndFlush(entity);
    audit.add(
        actors.current().id(),
        "Organization",
        entity.id,
        "SAVE_TREE",
        SqlRecords.mutable("name", name, "parentId", parent, "region", region, "reason", reason));
    long saved = entity.id;
    return organizationTree().stream()
        .filter(o -> SqlRecords.id(o, "id") == saved)
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void setStoreGroup(Long id, String group) {
    store(id);
    stores.findById(id).orElseThrow().groupName = group;
  }
}
