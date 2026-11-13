package vn.synera.crm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.crm.CrmOperationsFacade;

@RestController
@RequestMapping("/api/v1")
public class CrmOperationsController {
  private final CrmOperationsFacade crm;
  private final IdempotencyService idem;

  public CrmOperationsController(CrmOperationsFacade crm, IdempotencyService idem) {
    this.crm = crm;
    this.idem = idem;
  }

  public record Quote(
      @NotNull Long storeId,
      @NotEmpty @Valid List<DocumentRequests.Line> lines,
      @NotNull LocalDate validUntil) {}

  public record State(@NotBlank String status, String reason) {
    public State {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Survey(
      @NotNull Long storeId,
      @NotNull LocalDate date,
      @NotEmpty @Valid List<DocumentRequests.Quantity> lines,
      String note) {}

  public record Campaign(
      @NotNull Long nppId, @NotBlank String name, String groupName, @NotBlank String message) {}

  public record Launch(@NotNull Instant dueAt) {}

  @GetMapping("/quotations")
  public ApiResponse<?> quotes(@RequestParam(required = false) Long storeId) {
    return ApiResponse.ok(crm.quotations(storeId));
  }

  @PostMapping("/quotations")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> quote(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Quote r) {
    return ApiResponse.ok(
        idem.execute(
            key, "quote.create", r, () -> crm.quotation(r.storeId(), r.lines(), r.validUntil())));
  }

  @PostMapping("/quotations/{id}/transition")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> state(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody State r) {
    return ApiResponse.ok(
        idem.execute(
            key, "quote.state." + id, r, () -> crm.quotationState(id, r.status(), r.reason())));
  }

  @GetMapping("/stores/{id}/stock-surveys")
  public ApiResponse<?> surveys(@PathVariable Long id) {
    return ApiResponse.ok(crm.surveys(id));
  }

  @PostMapping("/stock-surveys")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> survey(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Survey r) {
    return ApiResponse.ok(
        idem.execute(
            key, "survey.create", r, () -> crm.survey(r.storeId(), r.date(), r.lines(), r.note())));
  }

  @GetMapping("/campaigns")
  public ApiResponse<?> campaigns() {
    return ApiResponse.ok(crm.campaigns());
  }

  @PostMapping("/campaigns")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> campaign(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Campaign r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "campaign.create",
            r,
            () -> crm.campaign(r.nppId(), r.name(), r.groupName(), r.message())));
  }

  @PostMapping("/campaigns/{id}/launch")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> launch(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Launch r) {
    return ApiResponse.ok(
        idem.execute(key, "campaign.launch." + id, r, () -> crm.launch(id, r.dueAt())));
  }

  @GetMapping("/campaigns/{id}/progress")
  public ApiResponse<?> progress(@PathVariable Long id) {
    return ApiResponse.ok(crm.campaignProgress(id));
  }
}
