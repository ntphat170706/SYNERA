package vn.synera.iam.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.iam.SystemParametersFacade;

@RestController
@RequestMapping("/api/v1/admin/parameters")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class SystemParametersController {
  private final SystemParametersFacade settings;
  private final IdempotencyService idem;

  public SystemParametersController(SystemParametersFacade settings, IdempotencyService idem) {
    this.settings = settings;
    this.idem = idem;
  }

  public record Request(@NotNull BigDecimal value, String reason) {
    public Request {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(settings.list());
  }

  @PostMapping("/{name}")
  public ApiResponse<?> save(
      @PathVariable String name,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(key, "settings." + name, r, () -> settings.save(name, r.value(), r.reason())));
  }
}
