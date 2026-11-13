package vn.synera.ticket.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.ticket.TicketFacade;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
  private final TicketFacade tickets;
  private final IdempotencyService idem;

  public TicketController(TicketFacade tickets, IdempotencyService idem) {
    this.tickets = tickets;
    this.idem = idem;
  }

  public record Request(
      @NotNull Long nppId,
      Long storeId,
      Long orderId,
      Long lineId,
      @NotBlank String title,
      @NotBlank String category,
      @NotBlank String description) {}

  public record Transition(@NotBlank String status, String reason) {
    public Transition {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  public record Comment(@NotBlank String body, Long attachmentId) {}

  public record Assignment(@NotNull Long userId, String reason) {
    public Assignment {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable Long id) {
    return ApiResponse.ok(tickets.detail(id));
  }

  @PostMapping("/{id}/comments")
  @PreAuthorize("hasAuthority('TICKET_WRITE')")
  public ApiResponse<?> comment(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Comment r) {
    return ApiResponse.ok(
        idem.execute(
            key, "ticket.comment." + id, r, () -> tickets.comment(id, r.body(), r.attachmentId())));
  }

  @PostMapping("/{id}/assign")
  @PreAuthorize("hasAuthority('TICKET_WRITE')")
  public ApiResponse<?> assign(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Assignment r) {
    return ApiResponse.ok(
        idem.execute(
            key, "ticket.assign." + id, r, () -> tickets.assign(id, r.userId(), r.reason())));
  }

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(tickets.list());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('TICKET_WRITE')")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "ticket.create",
            r,
            () ->
                tickets.create(
                    r.nppId(),
                    r.storeId(),
                    r.orderId(),
                    r.lineId(),
                    r.title(),
                    r.category(),
                    r.description())));
  }

  @PostMapping("/{id}/transition")
  @PreAuthorize("hasAuthority('TICKET_WRITE')")
  public ApiResponse<?> transition(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Transition r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "ticket.transition." + id,
            r,
            () -> tickets.transition(id, r.status(), r.reason())));
  }
}
