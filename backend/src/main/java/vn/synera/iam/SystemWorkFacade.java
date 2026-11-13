package vn.synera.iam;

import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import vn.synera.iam.repo.AppUserRepository;

/** Scheduled work uses a non-login service identity; no HTTP token is created or exported. */
@Service
public class SystemWorkFacade {
  private final AppUserRepository users;
  private final RoleFacade roles;

  public SystemWorkFacade(AppUserRepository users, RoleFacade roles) {
    this.users = users;
    this.roles = roles;
  }

  public <T> T run(Supplier<T> action) {
    var user =
        users
            .findByUsername("scheduler@synera.internal")
            .filter(u -> u.enabled && u.role.equals("SCHEDULER"))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Provision the scheduler service account before enabling jobs"));
    var before = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    var jwt =
        Jwt.withTokenValue("internal-job-context-not-an-http-token")
            .header("alg", "internal")
            .subject(user.id.toString())
            .issuedAt(Instant.now())
            .claim("role", user.role)
            .claim("v", user.tokenVersion)
            .build();
    context.setAuthentication(
        new JwtAuthenticationToken(
            jwt, Permissions.of(user.role).stream().map(SimpleGrantedAuthority::new).toList()));
    SecurityContextHolder.setContext(context);
    try {
      return action.get();
    } finally {
      SecurityContextHolder.setContext(before);
    }
  }

  public <T> T runAs(Long userId, String workspaceKey, Supplier<T> action) {
    var user =
        users
            .findById(userId)
            .filter(u -> u.enabled && !u.role.equals("SCHEDULER"))
            .orElseThrow(() -> new IllegalStateException("Report owner is disabled"));
    var before = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    var jwt =
        Jwt.withTokenValue("internal-report-context-not-an-http-token")
            .header("alg", "internal")
            .subject(user.id.toString())
            .issuedAt(Instant.now())
            .claim("w", workspaceKey)
            .claim("v", user.tokenVersion)
            .build();
    // Resolve through TokenUserFacade's selected-workspace path before the action, including
    // revocations.
    var grant = roles.resolve(userId, workspaceKey);
    if (!grant.authorities().contains("REPORT_READ"))
      throw new IllegalStateException("Report permission revoked");
    context.setAuthentication(
        new JwtAuthenticationToken(
            jwt, grant.authorities().stream().map(SimpleGrantedAuthority::new).toList()));
    SecurityContextHolder.setContext(context);
    try {
      return action.get();
    } finally {
      SecurityContextHolder.setContext(before);
    }
  }
}
