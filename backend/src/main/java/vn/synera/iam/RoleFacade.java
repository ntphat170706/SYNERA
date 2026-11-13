package vn.synera.iam;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.common.*;
import vn.synera.iam.domain.AppUser;
import vn.synera.iam.repo.AppUserRepository;

/**
 * Authorities are evaluated only for the selected, server-authorized workspace. Never union scopes.
 */
@Service
@Transactional(readOnly = true)
public class RoleFacade {
  public record Workspace(
      String key,
      String roleCode,
      String role,
      String scopeType,
      String scopeId,
      Long nppId,
      String region,
      Set<String> authorities) {}

  private final JdbcTemplate jdbc;
  private final AppUserRepository users;
  private final Jsons json;
  private final AuditService audit;

  public RoleFacade(JdbcTemplate jdbc, AppUserRepository users, Jsons json, AuditService audit) {
    this.jdbc = jdbc;
    this.users = users;
    this.json = json;
    this.audit = audit;
  }

  public String baseRole(String code) {
    if (Permissions.ROLES.contains(code)) return code;
    var rows =
        jdbc.queryForList(
            "select base_role from role where code=? and active=true", String.class, code);
    if (rows.isEmpty())
      throw new BusinessException("UNAUTHENTICATED", "Vai trò đã bị thu hồi.", 401);
    return rows.get(0);
  }

  public boolean exists(String code) {
    return Permissions.ROLES.contains(code)
        || !jdbc.queryForList(
                "select code from role where code=? and active=true", String.class, code)
            .isEmpty();
  }

  private Set<String> permissions(String code) {
    if (Permissions.ROLES.contains(code)) return Permissions.of(code);
    var allowed = Permissions.of(baseRole(code));
    return Set.copyOf(
        jdbc
            .queryForList(
                "select permission_code from role_permission where role_code=?", String.class, code)
            .stream()
            .filter(allowed::contains)
            .toList());
  }

  private Workspace workspace(String roleCode, String type, String scope) {
    String base = baseRole(roleCode);
    if (type.equals("REGION") && scope.equals("ALL")) type = "ALL";
    Long npp = type.equals("NPP") ? Long.valueOf(scope) : null;
    String region = type.equals("REGION") ? scope : null;
    String key =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                json.write(List.of(roleCode, type, scope)).getBytes(StandardCharsets.UTF_8));
    return new Workspace(key, roleCode, base, type, scope, npp, region, permissions(roleCode));
  }

  private Workspace primary(AppUser user) {
    return workspace(
        user.role,
        user.nppId != null
            ? "NPP"
            : user.region != null && !user.region.isBlank() ? "REGION" : "ALL",
        user.nppId != null
            ? user.nppId.toString()
            : user.region != null && !user.region.isBlank() ? user.region : "ALL");
  }

  public List<Workspace> workspaces(Long userId) {
    var user =
        users.findById(userId).filter(u -> u.enabled).orElseThrow(BusinessException::missing);
    var grants = new LinkedHashMap<String, Workspace>();
    try {
      var first = primary(user);
      grants.put(first.key(), first);
    } catch (BusinessException ignored) {
    }
    for (var row :
        jdbc.queryForList(
            "select role_code,scope_type,scope_id from user_role_scope where user_id=?", userId)) {
      try {
        var grant =
            workspace(
                (String) row.get("role_code"),
                (String) row.get("scope_type"),
                (String) row.get("scope_id"));
        grants.put(grant.key(), grant);
      } catch (BusinessException ignored) {
      }
    }
    return List.copyOf(grants.values());
  }

  public Workspace resolve(Long userId, String key) {
    var user =
        users.findById(userId).filter(u -> u.enabled).orElseThrow(BusinessException::missing);
    if (key == null || key.isBlank()) return primary(user);
    return workspaces(userId).stream()
        .filter(w -> w.key().equals(key))
        .findFirst()
        .orElseThrow(
            () -> new BusinessException("UNAUTHENTICATED", "Phạm vi phiên đã bị thu hồi.", 401));
  }

  public boolean requiresOtp(Long id) {
    return workspaces(id).stream()
        .anyMatch(
            w ->
                Set.of("SYSTEM_ADMIN", "ACCOUNTANT_NSX", "REGION_MANAGER", "NSX_DIRECTOR")
                    .contains(w.role()));
  }

  public List<Map<String, Object>> definitions() {
    var result = new ArrayList<Map<String, Object>>();
    for (String role :
        Permissions.ROLES.stream().filter(r -> !r.equals("SCHEDULER")).sorted().toList())
      result.add(
          Map.of(
              "role",
              role,
              "name",
              role,
              "baseRole",
              role,
              "active",
              true,
              "version",
              0,
              "builtIn",
              true,
              "authorities",
              Permissions.of(role)));
    for (var row : jdbc.queryForList("select * from role order by code")) {
      String code = (String) row.get("code");
      if (Permissions.ROLES.contains(code)) continue;
      result.add(
          Map.of(
              "role",
              code,
              "name",
              row.get("name"),
              "baseRole",
              row.get("base_role"),
              "active",
              row.get("active"),
              "version",
              row.get("version"),
              "builtIn",
              false,
              "authorities",
              permissions(code)));
    }
    return result;
  }

  private Long caller() {
    return Long.valueOf(
        ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication())
            .getToken()
            .getSubject());
  }

  @Transactional
  public Object save(
      String code,
      String name,
      String base,
      Set<String> permissions,
      boolean active,
      Long version,
      String reason) {
    BusinessException.require(
        code.matches("[A-Z][A-Z0-9_]{2,49}")
            && !Permissions.ROLES.contains(code)
            && Permissions.ROLES.contains(base)
            && !base.equals("SCHEDULER")
            && !name.isBlank(),
        "Mã hoặc vai trò gốc không hợp lệ; vai trò mặc định được bảo vệ.");
    BusinessException.require(
        Permissions.of(base).containsAll(permissions),
        "Quyền tùy chỉnh phải nằm trong giới hạn vai trò gốc.");
    var existing = jdbc.queryForList("select * from role where code=? for update", code);
    if (existing.isEmpty())
      jdbc.update(
          "insert into role(code,name,base_role,active,version) values(?,?,?,?,0)",
          code,
          name,
          base,
          active);
    else {
      BusinessException.require(
          base.equals(existing.get(0).get("base_role")),
          "Không đổi loại vai trò gốc sau khi đã tạo.");
      if (version == null || ((Number) existing.get(0).get("version")).longValue() != version)
        throw BusinessException.conflict("OPTIMISTIC_LOCK", "Vai trò đã thay đổi.");
      jdbc.update(
          "update role set name=?,active=?,version=version+1 where code=?", name, active, code);
      jdbc.update("delete from role_permission where role_code=?", code);
    }
    for (String permission : permissions)
      jdbc.update(
          "insert into role_permission(role_code,permission_code) values(?,?)", code, permission);
    audit.add(
        caller(),
        "Role",
        0L,
        "SAVE",
        Map.of("role", code, "permissions", permissions, "reason", reason));
    return Map.of("role", code, "active", active);
  }

  @Transactional
  public Object assign(
      Long userId, String role, String type, String scope, boolean remove, String reason) {
    BusinessException.require(
        !userId.equals(caller()) && exists(role), "Không tự sửa phân công hoặc thiếu lý do.");
    String base = baseRole(role);
    BusinessException.require(
        Set.of("ALL", "NPP", "REGION").contains(type)
            && scope != null
            && !scope.isBlank()
            && scope.length() <= 150
            && !base.equals("SCHEDULER"),
        "Phạm vi không hợp lệ.");
    BusinessException.require(
        Permissions.nsx(base) ? !type.equals("NPP") : type.equals("NPP"),
        "Vai trò NSX dùng ALL/REGION; vai trò NPP dùng NPP.");
    if (base.equals("SYSTEM_ADMIN"))
      BusinessException.require(
          type.equals("ALL") && scope.equals("ALL"),
          "Quản trị hệ thống chỉ dùng phạm vi toàn hệ thống.");
    var u = users.lockById(userId).orElseThrow(BusinessException::missing);
    if (remove)
      BusinessException.require(
          !primary(u).key().equals(workspace(role, type, scope).key()),
          "Vai trò chính phải đổi tại chức năng quyền truy cập; không thể thu hồi như một phạm vi"
              + " bổ sung.");
    if (remove)
      jdbc.update(
          "delete from user_role_scope where user_id=? and role_code=? and scope_type=? and"
              + " scope_id=?",
          userId,
          role,
          type,
          scope);
    else if (jdbc.queryForList(
            "select role_code from user_role_scope where user_id=? and role_code=? and scope_type=?"
                + " and scope_id=?",
            userId,
            role,
            type,
            scope)
        .isEmpty())
      jdbc.update(
          "insert into user_role_scope(user_id,role_code,scope_type,scope_id) values(?,?,?,?)",
          userId,
          role,
          type,
          scope);
    u.tokenVersion++;
    jdbc.update(
        "update refresh_token set revoked_at=? where user_id=? and revoked_at is null",
        Timestamp.from(Instant.now()),
        userId);
    audit.add(
        caller(),
        "User",
        userId,
        remove ? "SCOPE_REVOKE" : "SCOPE_GRANT",
        Map.of("role", role, "scope", scope, "type", type, "reason", reason));
    return Map.of("userId", userId, "reauthenticationRequired", true);
  }

  public List<Long> scopedUserIds(Long npp) {
    return jdbc.queryForList(
        "select id from app_user where enabled=true and (npp_id=? or id in (select user_id from"
            + " user_role_scope where scope_type='NPP' and scope_id=?))",
        Long.class,
        npp,
        npp.toString());
  }
}
