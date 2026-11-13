package vn.synera.integration.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.synera.common.*;
import vn.synera.integration.StorageFacade;

@RestController
@RequestMapping("/api/v1/attachments")
public class StorageController {
  private final StorageFacade storage;
  private final IdempotencyService idem;

  public StorageController(StorageFacade storage, IdempotencyService idem) {
    this.storage = storage;
    this.idem = idem;
  }

  public record Presign(
      @NotNull Long nppId,
      @NotBlank String filename,
      @NotBlank String contentType,
      @Min(1) @Max(5242880) long sizeBytes) {}

  @PostMapping("/presign")
  public ApiResponse<?> presign(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Presign r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "attachment.presign",
            r,
            () -> storage.presign(r.nppId(), r.filename(), r.contentType(), r.sizeBytes())));
  }

  @PostMapping("/{id}/complete")
  public ApiResponse<?> complete(
      @PathVariable Long id, @RequestHeader("Idempotency-Key") String key) {
    return ApiResponse.ok(
        idem.execute(
            key, "attachment.complete." + id, java.util.Map.of(), () -> storage.complete(id)));
  }

  @PostMapping(value = "/local", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<?> local(@RequestParam Long nppId, @RequestParam MultipartFile file) {
    return ApiResponse.ok(storage.uploadLocal(nppId, file));
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<?> upload(@RequestParam Long nppId, @RequestParam MultipartFile file) {
    return ApiResponse.ok(storage.uploadFile(nppId, file));
  }

  @GetMapping("/{id}/content")
  public ResponseEntity<byte[]> content(@PathVariable Long id) {
    var f = storage.content(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.contentType()))
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(f.filename(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(f.bytes());
  }
}
