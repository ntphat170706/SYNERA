package vn.synera.iam;

import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.synera.iam.repo.AppUserRepository;

/** Immutable authentication projection: configuration never imports IAM entities/repositories. */
@Service
public class TokenUserFacade {
  public static final class TokenUser {
    public final boolean enabled;
    public final Long tokenVersion;
    public final String role, username;
    public final java.util.Set<String> authorities;

    TokenUser(
        boolean enabled,
        Long tokenVersion,
        String role,
        String username,
        java.util.Set<String> authorities) {
      this.enabled = enabled;
      this.tokenVersion = tokenVersion;
      this.role = role;
      this.username = username;
      this.authorities = authorities;
    }
  }

  private final AppUserRepository users;
  private final RoleFacade roles;

  public TokenUserFacade(AppUserRepository users, RoleFacade roles) {
    this.users = users;
    this.roles = roles;
  }

  public Optional<TokenUser> findById(Long id) {
    return findById(id, null);
  }

  public Optional<TokenUser> findById(Long id, String workspace) {
    try {
      return users
          .findById(id)
          .map(
              u -> {
                var grant = roles.resolve(id, workspace);
                return new TokenUser(
                    u.enabled, u.tokenVersion, grant.role(), u.username, grant.authorities());
              });
    } catch (vn.synera.common.BusinessException e) {
      return Optional.empty();
    }
  }
}
