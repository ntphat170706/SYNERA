package vn.synera.iam;

import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import vn.synera.common.BusinessException;
import vn.synera.iam.repo.AppUserRepository;

@Service
public class ActorFacade {
  public record Actor(Long id, String name, String role, Long nppId, String region) {
    public boolean nsx() {
      return Permissions.nsx(role);
    }

    public boolean sales() {
      return role.equals("SALES");
    }

    public boolean admin() {
      return role.equals("SYSTEM_ADMIN");
    }

    public boolean permitsNpp(Long id, String nppRegion) {
      return nsx()
          ? (region == null || region.isBlank() || region.equals(nppRegion))
          : Objects.equals(nppId, id);
    }
  }

  private final AppUserRepository users;
  private final RoleFacade roles;

  public ActorFacade(AppUserRepository users, RoleFacade roles) {
    this.users = users;
    this.roles = roles;
  }

  public boolean has(String permission) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
  }

  public void require(String permission) {
    if (!has(permission))
      throw new BusinessException("FORBIDDEN_SCOPE", "Không có quyền thực hiện thao tác.", 403);
  }

  public Actor current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt))
      throw new BusinessException("UNAUTHENTICATED", "Cần đăng nhập.", 401);
    Long id = Long.valueOf(jwt.getToken().getSubject());
    var u = users.findById(id).filter(x -> x.enabled).orElseThrow(BusinessException::missing);
    var grant = roles.resolve(id, jwt.getToken().getClaimAsString("w"));
    return new Actor(id, u.displayName, grant.role(), grant.nppId(), grant.region());
  }

  public Actor get(Long id) {
    var u = users.findById(id).filter(x -> x.enabled).orElseThrow(BusinessException::missing);
    var grant = roles.resolve(id, null);
    return new Actor(u.id, u.displayName, grant.role(), grant.nppId(), grant.region());
  }

  public void lockCurrent() {
    users.lockById(current().id()).orElseThrow(BusinessException::missing);
  }

  public void assertRep(Long repId, Long nppId) {
    BusinessException.require(
        roles.workspaces(repId).stream()
            .anyMatch(
                g ->
                    Objects.equals(g.nppId(), nppId)
                        && Set.of("SALES", "SUPERVISOR", "NPP_OWNER").contains(g.role())),
        "Nhân viên không thuộc NPP hoặc vai trò không phù hợp.");
  }

  public void assertOwnRep(Long repId) {
    var actor = current();
    if (actor.sales() && !actor.id().equals(repId)) throw BusinessException.missing();
  }

  public List<Actor> staff(Long nppId) {
    return staff(nppId, null);
  }

  public List<Actor> staff(Long nppId, String role) {
    return roles.scopedUserIds(nppId).stream()
        .map(id -> users.findById(id).orElseThrow())
        .flatMap(
            u ->
                roles.workspaces(u.id).stream()
                    .filter(w -> Objects.equals(w.nppId(), nppId))
                    .filter(w -> role == null || w.role().equals(role))
                    .sorted(java.util.Comparator.comparing(w -> !w.role().equals("SALES")))
                    .limit(1)
                    .map(w -> new Actor(u.id, u.displayName, w.role(), w.nppId(), w.region())))
        .toList();
  }

  public String scopeKey() {
    var auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    return roles
        .resolve(Long.valueOf(auth.getToken().getSubject()), auth.getToken().getClaimAsString("w"))
        .key();
  }

  public String email() {
    return users.findById(current().id()).orElseThrow(BusinessException::missing).username;
  }

  /** Names for references already scoped by the caller; includes inactive historical assignees. */
  public Map<Long, String> referencedDisplayNames(Collection<Long> ids) {
    require("REPORT_READ");
    if (!current().nsx())
      throw new BusinessException("FORBIDDEN_SCOPE", "Không có quyền đọc nhân sự mạng lưới.", 403);
    var result = new LinkedHashMap<Long, String>();
    for (var user : users.findAllById(ids)) result.put(user.id, user.displayName);
    return result;
  }
}
