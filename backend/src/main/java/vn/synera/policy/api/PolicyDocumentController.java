package vn.synera.policy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.policy.PolicyDocumentFacade;

@RestController
@RequestMapping("/api/v1/policy-documents")
public class PolicyDocumentController {
  private final PolicyDocumentFacade docs;
  private final IdempotencyService idem;

  public PolicyDocumentController(PolicyDocumentFacade docs, IdempotencyService idem) {
    this.docs = docs;
    this.idem = idem;
  }

  public record Draft(
      Long nppId, @NotBlank String title, @NotBlank String body, Long attachmentId) {}

  public record Reason(String reason) {
    public Reason {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(docs.list());
  }

  @PostMapping
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Draft r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "policy.document",
            r,
            () -> docs.create(r.nppId(), r.title(), r.body(), r.attachmentId())));
  }

  @PostMapping("/{id}/publish")
  public ApiResponse<?> publish(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Reason r) {
    return ApiResponse.ok(
        idem.execute(key, "policy.document.publish." + id, r, () -> docs.publish(id, r.reason())));
  }

  @PostMapping("/{id}/acknowledge")
  public ApiResponse<?> acknowledge(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(
            key, "policy.document.ack." + id, java.util.Map.of(), () -> docs.acknowledge(id)));
  }
}
