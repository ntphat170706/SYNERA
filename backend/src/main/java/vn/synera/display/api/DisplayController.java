package vn.synera.display.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.display.domain.*;
import vn.synera.display.repo.*;
import vn.synera.iam.ActorFacade;
import vn.synera.integration.StorageFacade;

@RestController
@RequestMapping("/api/v1/display-audits")
public class DisplayController {
  private final DisplayCriteriaRepository criteria;
  private final DisplayAuditRepository audits;
  private final CatalogFacade catalog;
  private final StorageFacade storage;
  private final ActorFacade actors;
  private final IdempotencyService idem;
  private final AuditService audit;
  private final Jsons json;
  private final vn.synera.display.AiFacade ai;

  public DisplayController(
      DisplayCriteriaRepository criteria,
      DisplayAuditRepository audits,
      CatalogFacade catalog,
      StorageFacade storage,
      ActorFacade actors,
      IdempotencyService idem,
      AuditService audit,
      Jsons json,
      vn.synera.display.AiFacade ai) {
    this.criteria = criteria;
    this.audits = audits;
    this.catalog = catalog;
    this.storage = storage;
    this.actors = actors;
    this.idem = idem;
    this.audit = audit;
    this.json = json;
    this.ai = ai;
  }

  public record Criteria(@NotBlank String name, @NotEmpty Map<String, Object> rules, Long nppId) {}

  public record Upload(
      @NotNull Long storeId, @NotNull Long criteriaId, @NotNull Long attachmentId) {}

  public record Review(@Min(0) @Max(100) double score, String reason) {
    public Review {
      reason = java.util.Objects.toString(reason, "");
    }
  }

  @GetMapping("/criteria")
  public ApiResponse<?> criteria() {
    return ApiResponse.ok(
        criteria
            .findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.equal(r.get("status"), "ACTIVE"),
                        cb.or(
                            cb.isNull(r.get("nppId")), r.get("nppId").in(catalog.visibleNppIds()))))
            .stream()
            .map(c -> Map.of("id", c.id, "name", c.name, "rules", json.read(c.rulesJson)))
            .toList());
  }

  @PostMapping("/criteria")
  @PreAuthorize("hasAuthority('DISPLAY_REVIEW')")
  public ApiResponse<?> criteria(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Criteria r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "display.criteria",
            r,
            () -> {
              var c = new DisplayCriteria();
              c.nppId = actors.current().nsx() ? r.nppId() : actors.current().nppId();
              if (c.nppId != null) catalog.npp(c.nppId);
              else
                BusinessException.require(
                    actors.current().nsx() && actors.current().region() == null,
                    "Tiêu chí chung cần phạm vi toàn quốc.");
              c.name = r.name();
              c.rulesJson = json.write(r.rules());
              c.status = "ACTIVE";
              criteria.saveAndFlush(c);
              return Map.of("id", c.id);
            }));
  }

  @GetMapping
  public ApiResponse<?> list() {
    var ids = catalog.visibleNppIds();
    return ApiResponse.ok(
        audits.findAll((r, q, cb) -> r.get("nppId").in(ids)).stream()
            .filter(
                a ->
                    !actors.current().sales()
                        || catalog.stores("").stream().anyMatch(s -> s.id().equals(a.storeId)))
            .map(
                a ->
                    SqlRecords.mutable(
                        "id",
                        a.id,
                        "storeId",
                        a.storeId,
                        "status",
                        a.status,
                        "provider",
                        a.provider,
                        "finalScore",
                        a.finalScore,
                        "aiScore",
                        a.aiScore,
                        "confidence",
                        a.confidence,
                        "attachmentId",
                        a.attachmentId,
                        "modelVersion",
                        a.modelVersion,
                        "findings",
                        a.resultJson == null ? null : json.read(a.resultJson)))
            .toList());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('SALES_CREATE')")
  public ApiResponse<?> submit(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Upload r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "display.submit",
            r,
            () -> {
              var s = catalog.store(r.storeId());
              storage.assertImage(r.attachmentId(), s.nppId());
              var c = criteria.findById(r.criteriaId()).orElseThrow(BusinessException::missing);
              BusinessException.require(c.status.equals("ACTIVE"), "Tiêu chí chưa phát hành.");
              BusinessException.require(
                  c.nppId == null || c.nppId.equals(s.nppId()),
                  "Bộ tiêu chí khác phạm vi cửa hàng.");
              var a = new DisplayAudit();
              a.nppId = s.nppId();
              a.storeId = s.id();
              a.criteriaId = c.id;
              a.attachmentId = r.attachmentId();
              a.status = "NEEDS_REVIEW";
              a.provider = "MANUAL_NO_AI_CONFIGURED";
              audits.saveAndFlush(a);
              ai.enqueue(a);
              return Map.of("id", a.id, "status", a.status, "provider", a.provider);
            }));
  }

  @PostMapping("/{id}/review")
  @PreAuthorize("hasAuthority('DISPLAY_REVIEW')")
  public ApiResponse<?> review(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Review r) {
    return ApiResponse.ok(
        idem.execute(
            key,
            "display.review." + id,
            r,
            () -> {
              var a = audits.lockById(id).orElseThrow(BusinessException::missing);
              catalog.npp(a.nppId);
              BusinessException.require(
                  a.status.equals("NEEDS_REVIEW") && Double.isFinite(r.score()),
                  "Ảnh không ở hàng đợi duyệt hoặc điểm không hợp lệ.");
              a.finalScore = r.score();
              a.reviewedBy = actors.current().id();
              a.status = "REVIEWED";
              audit.add(
                  a.reviewedBy,
                  "DisplayAudit",
                  id,
                  "MANUAL_REVIEW",
                  Map.of("score", r.score(), "reason", r.reason()));
              return Map.of("id", id, "status", a.status, "score", r.score());
            }));
  }
}
