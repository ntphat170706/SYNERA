package vn.synera.iam;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.iam.domain.AppUser;
import vn.synera.iam.repo.AppUserRepository;

@Service
public class BootstrapUsers {
  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final JdbcTemplate jdbc;

  public BootstrapUsers(AppUserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc) {
    this.users = users;
    this.encoder = encoder;
    this.jdbc = jdbc;
  }

  @Transactional
  public void initializeProduction(String email, String password) {
    if (jdbc.queryForObject(
            "select count(*) from app_user where username in"
                + " ('admin@synera.local','npp@synera.local','sales@synera.local')",
            Integer.class)
        > 0)
      throw new IllegalStateException(
          "Production must use a dedicated database without local fixture identities");
    boolean existing = users.count() != 0;
    if (!existing && (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || password.length() < 16))
      throw new IllegalStateException(
          "Production bootstrap requires an email and a password of at least 16 characters");
    for (String role : Permissions.ROLES)
      jdbc.update(
          "insert into role(code,name) select ?,? where not exists(select 1 from role where"
              + " code=?)",
          role,
          role,
          role);
    for (String permission : Permissions.ALL)
      jdbc.update(
          "insert into permission(code) select ? where not exists(select 1 from permission where"
              + " code=?)",
          permission,
          permission);
    for (String role : Permissions.ROLES)
      for (String permission : Permissions.of(role))
        jdbc.update(
            "insert into role_permission(role_code,permission_code) select ?,? where not"
                + " exists(select 1 from role_permission where role_code=? and permission_code=?)",
            role,
            permission,
            role,
            permission);
    if (existing) return;
    var admin = new AppUser();
    admin.username = email.toLowerCase(Locale.ROOT);
    admin.displayName = "Quản trị SYNERA";
    admin.role = "SYSTEM_ADMIN";
    admin.passwordHash = encoder.encode(password);
    admin.enabled = true;
    admin.otpRequired = true;
    users.saveAndFlush(admin);
    var scheduler = new AppUser();
    scheduler.username = "scheduler@synera.internal";
    scheduler.displayName = "Lịch hệ thống";
    scheduler.role = "SCHEDULER";
    scheduler.passwordHash = encoder.encode(UUID.randomUUID().toString());
    scheduler.enabled = true;
    users.saveAndFlush(scheduler);
  }

  @Transactional
  public Map<String, Long> seed(String password, Long nppA, Long nppB) {
    if (password == null || password.length() < 12)
      throw new IllegalStateException("BOOTSTRAP_PASSWORD must have 12+ characters");
    for (String role : Permissions.ROLES) {
      jdbc.update(
          "insert into role(code,name) select ?,? where not exists(select 1 from role where"
              + " code=?)",
          role,
          role,
          role);
      for (String permission : Permissions.of(role)) {
        jdbc.update(
            "insert into permission(code) select ? where not exists(select 1 from permission where"
                + " code=?)",
            permission,
            permission);
        jdbc.update(
            "insert into role_permission(role_code,permission_code) select ?,? where not"
                + " exists(select 1 from role_permission where role_code=? and permission_code=?)",
            role,
            permission,
            role,
            permission);
      }
    }
    Map<String, Long> result = new LinkedHashMap<>();
    String hash = encoder.encode(password);
    for (String[] spec :
        new String[][] {
          {"admin@synera.local", "Quản trị SYNERA", "SYSTEM_ADMIN", ""},
          {"npp@synera.local", "Minh Phát", "NPP_OWNER", "A"},
          {"sales@synera.local", "Trần Minh Anh", "SALES", "A"},
          {"npp-b@synera.local", "Hoàng Gia", "NPP_OWNER", "B"},
          {"accountant@synera.local", "Kế toán NSX", "ACCOUNTANT_NSX", ""},
          {"manager-north@synera.local", "Quản lý miền Bắc", "REGION_MANAGER", "NORTH"},
          {"scheduler@synera.internal", "Lịch hệ thống", "SCHEDULER", ""}
        }) {
      var user =
          users
              .findByUsername(spec[0])
              .orElseGet(
                  () -> {
                    var u = new AppUser();
                    u.username = spec[0];
                    u.displayName = spec[1];
                    u.role = spec[2];
                    u.passwordHash = hash;
                    u.enabled = true;
                    u.otpRequired =
                        Set.of("SYSTEM_ADMIN", "ACCOUNTANT_NSX", "REGION_MANAGER").contains(u.role);
                    if (spec[3].equals("A")) u.nppId = nppA;
                    else if (spec[3].equals("B")) u.nppId = nppB;
                    else if (!spec[3].isEmpty()) u.region = spec[3];
                    return users.saveAndFlush(u);
                  });
      result.put(spec[0], user.id);
      jdbc.update(
          "insert into user_role_scope(user_id,role_code,scope_type,scope_id) select ?,?,?,? where"
              + " not exists(select 1 from user_role_scope where user_id=? and role_code=?)",
          user.id,
          user.role,
          user.nppId == null ? "REGION" : "NPP",
          Objects.toString(user.nppId, Objects.toString(user.region, "ALL")),
          user.id,
          user.role);
    }
    return result;
  }
}
