package vn.synera.report.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.common.*;
import vn.synera.report.ReportExportFacade;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('REPORT_READ')")
public class ReportExportController {
  private final ReportExportFacade exports;
  private final IdempotencyService idem;

  public ReportExportController(ReportExportFacade exports, IdempotencyService idem) {
    this.exports = exports;
    this.idem = idem;
  }

  public record Request(
      @NotBlank String type,
      @NotBlank String format,
      @NotNull LocalDate from,
      @NotNull LocalDate to) {}

  public record Schedule(@NotBlank String type, @NotBlank String cadence) {}

  @GetMapping("/exports")
  public ApiResponse<?> list() {
    return ApiResponse.ok(exports.list());
  }

  @PostMapping("/exports")
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Request r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "report.export",
            r,
            () -> exports.create(r.type(), r.format(), r.from(), r.to(), null)));
  }

  @GetMapping("/exports/{id}/download")
  public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
    var f = exports.download(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + f.filename())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(new FileSystemResource(f.path()));
  }

  @GetMapping("/schedules")
  public ApiResponse<?> schedules() {
    return ApiResponse.ok(exports.schedules());
  }

  @PostMapping("/schedules")
  public ApiResponse<?> schedule(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Schedule r) {
    return ApiResponse.ok(
        idem.execute(key, "report.schedule", r, () -> exports.schedule(r.type(), r.cadence())));
  }

  @PostMapping("/schedules/{id}/cancel")
  public ApiResponse<?> cancel(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "report.schedule.cancel." + id,
            java.util.Map.of(),
            () -> exports.cancelSchedule(id)));
  }
}
