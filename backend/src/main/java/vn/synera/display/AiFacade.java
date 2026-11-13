package vn.synera.display;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.common.*;
import vn.synera.display.domain.DisplayAudit;
import vn.synera.display.repo.*;
import vn.synera.integration.StorageFacade;

@Service
@Transactional
public class AiFacade {
  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final DisplayAuditRepository audits;
  private final DisplayCriteriaRepository criteria;
  private final StorageFacade storage;
  private final ObjectProvider<AiClient> clients;
  private final Jsons json;
  private final AuditService audit;
  private final double threshold;

  public AiFacade(
      JdbcTemplate jdbc,
      SqlRecords sql,
      DisplayAuditRepository audits,
      DisplayCriteriaRepository criteria,
      StorageFacade storage,
      ObjectProvider<AiClient> clients,
      Jsons json,
      AuditService audit,
      @Value("${synera.ai.confidence-threshold:0.8}") double threshold) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.audits = audits;
    this.criteria = criteria;
    this.storage = storage;
    this.clients = clients;
    this.json = json;
    this.audit = audit;
    this.threshold = threshold;
    if (threshold < 0 || threshold > 1)
      throw new IllegalStateException("Invalid AI confidence threshold");
  }

  public String enqueue(DisplayAudit a) {
    var client = clients.getIfAvailable();
    a.provider = client == null ? "MANUAL_NO_AI_CONFIGURED" : client.provider();
    a.status = client == null ? "NEEDS_REVIEW" : "SUBMITTED";
    String hash = storage.contentHash(a.attachmentId, a.nppId);
    boolean duplicate =
        !jdbc.queryForList(
                "select d.id from display_audit d join attachment f on f.id=d.attachment_id where"
                    + " d.id<>? and d.npp_id=? and f.content_hash=?",
                a.id,
                a.nppId,
                hash)
            .isEmpty();
    if (duplicate) {
      a.status = "NEEDS_REVIEW";
      a.resultJson =
          json.write(
              Map.of(
                  "duplicateImage",
                  true,
                  "note",
                  "Ảnh đã được gửi trước đó; cần kiểm tra thủ công."));
      return a.status;
    }
    if (client != null)
      sql.insert(
          "ai_job",
          SqlRecords.mutable(
              "audit_id",
              a.id,
              "status",
              "PENDING",
              "available_at",
              Timestamp.from(Instant.now()),
              "created_at",
              Timestamp.from(Instant.now())));
    return a.status;
  }

  public void process(Long jobId) {
    var job = sql.row("ai_job", jobId, true);
    if (!job.get("status").equals("PENDING")) return;
    var a = audits.lockById(SqlRecords.id(job, "audit_id")).orElseThrow();
    if (!a.status.equals("SUBMITTED")) {
      jdbc.update("update ai_job set status='CANCELLED' where id=?", jobId);
      return;
    }
    var file = storage.content(a.attachmentId);
    var c = criteria.findById(a.criteriaId).orElseThrow();
    var client = clients.getIfAvailable();
    if (client == null) throw new IllegalStateException("AI client unavailable");
    var result = client.score(a.id, file.contentType(), file.bytes(), json.read(c.rulesJson));
    if (result != null) {
      apply(a, result, client.provider().startsWith("MOCK"));
      jdbc.update(
          "update ai_job set status='DONE',finished_at=? where id=?",
          Timestamp.from(Instant.now()),
          jobId);
    } else {
      a.status = "PROCESSING";
      jdbc.update(
          "update ai_job set status='WAITING_CALLBACK',available_at=? where id=?",
          Timestamp.from(Instant.now().plusSeconds(600)),
          jobId);
    }
  }

  private void apply(DisplayAudit a, AiClient.Result result, boolean mock) {
    BusinessException.require(
        Double.isFinite(result.score())
            && result.score() >= 0
            && result.score() <= 100
            && Double.isFinite(result.confidence())
            && result.confidence() >= 0
            && result.confidence() <= 1
            && result.modelVersion() != null
            && result.modelVersion().length() <= 100,
        "Kết quả AI không hợp lệ.");
    a.aiScore = result.score();
    a.confidence = result.confidence();
    a.modelVersion = result.modelVersion();
    a.resultJson = json.write(result.findings());
    a.status = mock || result.confidence() < threshold ? "NEEDS_REVIEW" : "AI_SCORED";
    if (a.status.equals("AI_SCORED")) a.finalScore = result.score();
    audit.add(
        null,
        "DisplayAudit",
        a.id,
        "AI_RESULT",
        Map.of("provider", a.provider, "confidence", result.confidence(), "status", a.status));
  }

  public Object callback(Long id, AiClient.Result result) {
    var a = audits.lockById(id).orElseThrow(BusinessException::missing);
    BusinessException.require(
        a.provider.equals("HTTP_AI") && Set.of("SUBMITTED", "PROCESSING").contains(a.status),
        "Ảnh đã xử lý hoặc không dùng nhà cung cấp HTTP.");
    apply(a, result, false);
    jdbc.update(
        "update ai_job set status='DONE',finished_at=? where audit_id=?",
        Timestamp.from(Instant.now()),
        id);
    return Map.of("id", id, "status", a.status);
  }

  public void failed(Long id) {
    var job = sql.row("ai_job", id, true);
    if (!job.get("status").equals("PENDING")) return;
    int attempts = ((Number) job.get("attempts")).intValue() + 1;
    jdbc.update(
        "update ai_job set attempts=?,status=?,available_at=?,last_error=? where id=?",
        attempts,
        attempts >= 5 ? "DEAD" : "PENDING",
        Timestamp.from(Instant.now().plusSeconds(30L * attempts)),
        "AI_REQUEST_FAILED",
        id);
    if (attempts >= 5) {
      var a = audits.lockById(SqlRecords.id(job, "audit_id")).orElseThrow();
      a.status = "NEEDS_REVIEW";
    }
  }

  public int expireCallbacks() {
    int count = 0;
    for (var job :
        jdbc.queryForList(
            "select * from ai_job where status='WAITING_CALLBACK' and available_at<? for update",
            Timestamp.from(Instant.now()))) {
      var image = audits.lockById(SqlRecords.id(job, "audit_id")).orElseThrow();
      if (Set.of("SUBMITTED", "PROCESSING").contains(image.status)) image.status = "NEEDS_REVIEW";
      jdbc.update(
          "update ai_job set status='TIMED_OUT',last_error='CALLBACK_TIMEOUT',finished_at=? where"
              + " id=?",
          Timestamp.from(Instant.now()),
          job.get("id"));
      count++;
    }
    return count;
  }
}
