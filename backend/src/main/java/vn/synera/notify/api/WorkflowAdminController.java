package vn.synera.notify.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.notify.WorkflowFacade;

@RestController
@RequestMapping("/api/v1/admin/workflows")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class WorkflowAdminController {
  private final WorkflowFacade workflow;
  private final IdempotencyService idem;

  public WorkflowAdminController(WorkflowFacade workflow, IdempotencyService idem) {
    this.workflow = workflow;
    this.idem = idem;
  }

  public record Request(
      @NotBlank String type, @NotEmpty List<WorkflowFacade.Definition> steps, String reason) {
    public Request {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(workflow.definitions());
  }

  @PostMapping
  public ApiResponse<?> save(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "workflow.configure",
            r,
            () -> workflow.configure(r.type(), r.steps(), r.reason())));
  }
}
