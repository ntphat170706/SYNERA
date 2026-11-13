package vn.synera.catalog;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;

@Service
@Transactional
public class ReferenceFacade {
  private static final Set<String> KINDS =
      Set.of(
          "CATEGORY", "BRAND", "REGION", "PROVINCE", "WARD", "STORE_GROUP", "NPP_GROUP", "REASON");
  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;

  public ReferenceFacade(
      JdbcTemplate jdbc,
      SqlRecords sql,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
  }

  private void kind(String kind) {
    BusinessException.require(KINDS.contains(kind), "Loại danh mục không hợp lệ.");
  }

  public List<Map<String, Object>> list(String kind) {
    kind(kind);
    var ids = catalog.visibleNppIds();
    var args = new ArrayList<Object>();
    args.add(kind);
    args.addAll(ids);
    String scope =
        ids.isEmpty()
            ? "npp_id is null"
            : "(npp_id is null or npp_id in ("
                + String.join(",", Collections.nCopies(ids.size(), "?"))
                + "))";
    return jdbc.queryForList(
        "select * from catalog_reference where kind=? and " + scope + " order by name limit 2000",
        args.toArray());
  }

  private Map<String, Object> owned(long id, boolean lock) {
    var row = sql.row("catalog_reference", id, lock);
    if (row.get("npp_id") != null) catalog.npp(SqlRecords.id(row, "npp_id"));
    return row;
  }

  public Object members(Long id) {
    owned(id, false);
    var npps = catalog.visibleNppIds();
    var stores = catalog.stores("").stream().map(CatalogFacade.Store::id).toList();
    return jdbc.queryForList("select * from catalog_membership where reference_id=?", id).stream()
        .filter(
            m ->
                m.get("member_type").equals("NPP")
                    ? npps.contains(SqlRecords.id(m, "member_id"))
                    : stores.contains(SqlRecords.id(m, "member_id")))
        .toList();
  }

  public Object assignMembers(Long id, List<Long> members, String reason, boolean remove) {
    var reference = owned(id, true);
    BusinessException.require(
        remove || Boolean.TRUE.equals(reference.get("active")),
        "Không thêm thành viên vào nhóm đã ngừng dùng.");
    boolean stores = reference.get("kind").equals("STORE_GROUP");
    BusinessException.require(
        (stores || reference.get("kind").equals("NPP_GROUP"))
            && members != null
            && members.size() <= 2000,
        "Nhóm hoặc danh sách thành viên không hợp lệ.");
    if (stores)
      BusinessException.require(
          actors.has("ROUTE_WRITE")
              || actors.has("CATALOG_WRITE")
              || (actors.has("SALES_APPROVE") && !actors.current().sales()),
          "Phân nhóm giá/chăm sóc cửa hàng cần quyền quản lý tuyến hoặc danh mục.");
    else actors.require("CATALOG_WRITE");
    for (Long member : members.stream().distinct().toList()) {
      if (stores) {
        var store = catalog.store(member);
        BusinessException.require(
            reference.get("npp_id") == null
                || store.nppId().equals(SqlRecords.id(reference, "npp_id")),
            "Cửa hàng khác phạm vi nhóm.");
        if (!remove) catalog.setStoreGroup(member, (String) reference.get("code"));
      } else {
        BusinessException.require(actors.current().nsx(), "Chỉ NSX phân nhóm NPP.");
        catalog.npp(member);
      }
      jdbc.update(
          "delete from catalog_membership where reference_id=? and member_type=? and member_id=?",
          id,
          stores ? "STORE" : "NPP",
          member);
      if (!remove)
        jdbc.update(
            "insert into catalog_membership(reference_id,member_type,member_id,created_at)"
                + " values(?,?,?,CURRENT_TIMESTAMP)",
            id,
            stores ? "STORE" : "NPP",
            member);
      else if (stores && catalog.store(member).groupName().equals(reference.get("code"))) {
        var remaining =
            jdbc.queryForList(
                "select r.code from catalog_membership m join catalog_reference r on"
                    + " r.id=m.reference_id where m.member_type='STORE' and m.member_id=? and"
                    + " r.kind='STORE_GROUP' and r.active=true order by r.id",
                String.class,
                member);
        catalog.setStoreGroup(member, remaining.isEmpty() ? "C" : remaining.get(0));
      }
    }
    audit.add(
        actors.current().id(),
        "CatalogReference",
        id,
        "ASSIGN_MEMBERS",
        Map.of("members", members, "reason", reason));
    return members(id);
  }

  public Object save(
      Long id,
      String kind,
      String code,
      String name,
      Long parent,
      Long npp,
      boolean active,
      Long version,
      String reason) {
    kind(kind);
    BusinessException.require(
        code != null && code.matches("[A-Za-z0-9_-]{1,80}") && name != null && !name.isBlank(),
        "Mã, tên hoặc lý do không hợp lệ.");
    if (npp != null) catalog.npp(npp);
    BusinessException.require(
        actors.current().nsx() || kind.equals("STORE_GROUP") && npp != null,
        "Chỉ NSX quản lý danh mục chung.");
    if (parent != null) {
      var root = owned(parent, false);
      BusinessException.require(
          Objects.equals(root.get("npp_id"), npp) || root.get("npp_id") == null,
          "Danh mục cha khác phạm vi.");
      String expected =
          switch (kind) {
            case "PROVINCE" -> "REGION";
            case "WARD" -> "PROVINCE";
            default -> kind;
          };
      BusinessException.require(
          expected.equals(root.get("kind")), "Loại danh mục cha không phù hợp.");
      var seen = new HashSet<Long>();
      Long next = parent;
      while (next != null) {
        BusinessException.require(
            !next.equals(id) && seen.add(next), "Cây danh mục không được có vòng lặp.");
        var row = owned(next, false);
        next = row.get("parent_id") == null ? null : SqlRecords.id(row, "parent_id");
      }
    }
    long result;
    if (id == null)
      result =
          sql.insert(
              "catalog_reference",
              SqlRecords.versioned(
                  "kind",
                  kind,
                  "code",
                  code,
                  "name",
                  name,
                  "parent_id",
                  parent,
                  "npp_id",
                  npp,
                  "active",
                  active));
    else {
      var existing = owned(id, true);
      BusinessException.require(
          existing.get("kind").equals(kind) && Objects.equals(existing.get("npp_id"), npp),
          "Không đổi loại/phạm vi của danh mục đã dùng.");
      if (version == null || SqlRecords.id(existing, "version") != version)
        throw BusinessException.conflict(
            "OPTIMISTIC_LOCK", "Danh mục đã thay đổi. Tải lại trước khi lưu.");
      jdbc.update(
          "update catalog_reference set"
              + " code=?,name=?,parent_id=?,active=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
              + " where id=?",
          code,
          name,
          parent,
          active,
          id);
      result = id;
    }
    audit.add(
        actors.current().id(),
        "CatalogReference",
        result,
        id == null ? "CREATE" : "UPDATE",
        Map.of("kind", kind, "reason", reason));
    return owned(result, false);
  }
}
