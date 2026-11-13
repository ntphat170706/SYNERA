package vn.synera.crm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.crm.CrmFacade;
import vn.synera.salesorder.SalesFacade;

@RestController
@RequestMapping("/api/v1")
public class CrmController {
  private final CrmFacade crm;
  private final CatalogFacade catalog;
  private final SalesFacade sales;
  private final IdempotencyService idem;
  private final vn.synera.crm.StoreProfileFacade profiles;

  public CrmController(
      CrmFacade crm,
      CatalogFacade catalog,
      SalesFacade sales,
      IdempotencyService idem,
      vn.synera.crm.StoreProfileFacade profiles) {
    this.crm = crm;
    this.catalog = catalog;
    this.sales = sales;
    this.idem = idem;
    this.profiles = profiles;
  }

  public record LeadReq(
      @NotNull Long nppId,
      @NotNull Long repId,
      @NotBlank String name,
      @NotBlank String phone,
      @NotBlank String address,
      Double latitude,
      Double longitude) {}

  public record State(@NotBlank String status, String reason) {
    public State {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record AppointmentReq(
      @NotNull Long storeId, @NotBlank String title, @NotNull Instant dueAt) {}

  public record InteractionReq(
      @NotNull Long storeId, @NotBlank String kind, @NotBlank String note) {}

  @GetMapping("/leads")
  public ApiResponse<?> leads() {
    return ApiResponse.ok(crm.leads());
  }

  @PostMapping("/leads")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> lead(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody LeadReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "lead.create",
            r,
            () ->
                crm.lead(
                    r.nppId(),
                    r.repId(),
                    r.name(),
                    r.phone(),
                    r.address(),
                    r.latitude(),
                    r.longitude())));
  }

  @PostMapping("/leads/{id}/transition")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> move(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody State r) {
    return ApiResponse.ok(
        idem.execute(
            key, "lead.transition." + id, r, () -> crm.moveLead(id, r.status(), r.reason())));
  }

  @GetMapping("/stores/{id}/360")
  public ApiResponse<?> profile(@PathVariable Long id) {
    return ApiResponse.ok(profiles.summary(id));
  }

  @GetMapping("/stores/{id}/360/{section}")
  public ApiResponse<?> profileSection(
      @PathVariable Long id,
      @PathVariable String section,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(profiles.section(id, section, page, size));
  }

  @GetMapping("/appointments")
  public ApiResponse<?> appointments(@RequestParam(required = false) Long storeId) {
    return ApiResponse.ok(crm.appointments(storeId));
  }

  @PostMapping("/appointments")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> appointment(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody AppointmentReq r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "appointment.create",
            r,
            () -> crm.appointment(r.storeId(), r.title(), r.dueAt())));
  }

  @PostMapping("/appointments/{id}/complete")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> complete(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DocumentRequests.Reason r) {
    return ApiResponse.ok(
        idem.execute(
            key, "appointment.complete." + id, r, () -> crm.finishAppointment(id, r.reason())));
  }

  @PostMapping("/interactions")
  @PreAuthorize("hasAuthority('CRM_WRITE')")
  public ApiResponse<?> interaction(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody InteractionReq r) {
    return ApiResponse.ok(
        idem.execute(
            key, "interaction.create", r, () -> crm.interaction(r.storeId(), r.kind(), r.note())));
  }
}
