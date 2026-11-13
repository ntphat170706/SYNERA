package vn.synera.closing.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.closing.OperationalFacade;
import vn.synera.common.*;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAuthority('REPORT_READ')")
public class OperationalController {
  private final OperationalFacade operations;
  private final IdempotencyService idem;

  public OperationalController(OperationalFacade operations, IdempotencyService idem) {
    this.operations = operations;
    this.idem = idem;
  }

  public record Reason(String reason) {
    public Reason {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/alerts")
  public ApiResponse<?> list() {
    return ApiResponse.ok(operations.list());
  }

  @PostMapping("/alerts/{id}/acknowledge")
  public ApiResponse<?> ack(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "alert.ack." + id, r, () -> operations.acknowledge(id, r.reason())));
  }
}
