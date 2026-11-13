package vn.synera.notify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.notify.domain.*;
import vn.synera.notify.repo.*;

/**
 * Call while holding the owning business document lock. Steps are snapshotted on first approval.
 */
@Service
@Transactional
public class WorkflowFacade {
  private final WorkflowDefinitionRepository definitions;
  private final ApprovalRequestRepository requests;
  private final ActorFacade actors;
  private final CatalogFacade catalog;
  private final AuditService audit;

  public WorkflowFacade(
      WorkflowDefinitionRepository definitions,
      ApprovalRequestRepository requests,
      ActorFacade actors,
      CatalogFacade catalog,
      AuditService audit) {
    this.definitions = definitions;
    this.requests = requests;
    this.actors = actors;
    this.catalog = catalog;
    this.audit = audit;
  }

  public boolean approve(String type, Long id, Long npp, BigDecimal amount, String reason) {
    if (npp != null) catalog.npp(npp);

    var steps = requests.findByEntityTypeAndEntityIdOrderByStepNo(type, id);
    if (steps.isEmpty()) {
      var matching =
          definitions
              .findAll(
                  (r, q, cb) ->
                      cb.and(
                          cb.equal(r.get("entityType"), type),
                          cb.lessThanOrEqualTo(r.get("minAmount"), amount.abs())))
              .stream()
              .sorted(Comparator.comparing(d -> d.stepNo))
              .toList();
      BusinessException.require(!matching.isEmpty(), "Chưa cấu hình workflow cho tài liệu.");
      for (var def : matching) {
        var step = new ApprovalRequest();
        step.entityType = type;
        step.entityId = id;
        step.nppId = npp;
        step.stepNo = def.stepNo;
        step.approverRole = def.approverRole;
        step.status = "PENDING";
        step.dueAt = Instant.now().plusSeconds(Math.max(1, def.slaHours) * 3600L * def.stepNo);
        requests.save(step);
      }
      requests.flush();
      steps = requests.findByEntityTypeAndEntityIdOrderByStepNo(type, id);
    }
    var actor = actors.current();
    var next =
        steps.stream()
            .filter(s -> s.status.equals("PENDING"))
            .findFirst()
            .orElseThrow(
                () ->
                    BusinessException.conflict(
                        "INVALID_STATE_TRANSITION", "Workflow đã hoàn tất."));
    if (!actor.admin() && !actor.role().equals(next.approverRole) && !actors.has(next.approverRole))
      throw new BusinessException(
          "FORBIDDEN_SCOPE", "Bước này cần vai trò " + next.approverRole, 403);
    if (steps.stream().anyMatch(s -> Objects.equals(s.approvedBy, actor.id())))
      throw new BusinessException(
          "FORBIDDEN_SCOPE", "Các bước duyệt nhiều cấp cần người duyệt khác nhau.", 403);
    next.status = "APPROVED";
    next.approvedBy = actor.id();
    next.approvedAt = Instant.now();
    next.reason = reason;
    audit.add(
        actor.id(),
        type,
        id,
        "WORKFLOW_STEP_" + next.stepNo,
        Map.of("role", next.approverRole, "reason", reason));
    return steps.stream().noneMatch(s -> s.status.equals("PENDING"));
  }

  public List<?> pending() {
    var ids = catalog.visibleNppIds();
    var a = actors.current();
    return requests.findAll().stream()
        .filter(
            r ->
                r.status.equals("PENDING") && (r.nppId == null && a.nsx() || ids.contains(r.nppId)))
        .map(
            r ->
                Map.of(
                    "id",
                    r.id,
                    "entityType",
                    r.entityType,
                    "entityId",
                    r.entityId,
                    "step",
                    r.stepNo,
                    "requiredRole",
                    r.approverRole))
        .toList();
  }

  public void start(String type, Long id, Long npp, BigDecimal amount) {
    if (npp != null) catalog.npp(npp);
    if (!requests.findByEntityTypeAndEntityIdOrderByStepNo(type, id).isEmpty()) return;
    var matches =
        definitions
            .findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.equal(r.get("entityType"), type),
                        cb.lessThanOrEqualTo(r.get("minAmount"), amount.abs())))
            .stream()
            .sorted(Comparator.comparing(d -> d.stepNo))
            .toList();
    BusinessException.require(!matches.isEmpty(), "Chưa cấu hình workflow cho tài liệu.");
    long hours = 0;
    for (var def : matches) {
      var step = new ApprovalRequest();
      step.entityType = type;
      step.entityId = id;
      step.nppId = npp;
      step.stepNo = def.stepNo;
      step.approverRole = def.approverRole;
      step.status = "PENDING";
      hours += Math.max(1, def.slaHours);
      step.dueAt = Instant.now().plusSeconds(hours * 3600);
      requests.save(step);
    }
    requests.flush();
  }

  public void cancel(String type, Long id, String reason) {
    for (var step : requests.findByEntityTypeAndEntityIdOrderByStepNo(type, id))
      if (step.status.equals("PENDING")) {
        step.status = "CANCELLED";
        step.reason = reason;
      }
  }

  public Object definitions() {
    actors.require("USER_MANAGE");
    return definitions.findAll().stream()
        .map(
            d ->
                Map.of(
                    "id",
                    d.id,
                    "entityType",
                    d.entityType,
                    "step",
                    d.stepNo,
                    "role",
                    d.approverRole,
                    "minAmount",
                    d.minAmount,
                    "slaHours",
                    d.slaHours))
        .toList();
  }

  public record Definition(int step, String role, BigDecimal minAmount, int slaHours) {}

  public Object configure(String type, List<Definition> steps, String reason) {
    actors.require("USER_MANAGE");
    BusinessException.require(
        Set.of("SELL_IN", "TRADE_REQUEST", "POLICY", "ADJUSTMENT").contains(type)
            && steps != null
            && !steps.isEmpty()
            && steps.size() <= 10
            && steps.stream().allMatch(Objects::nonNull),
        "Loại workflow hoặc số bước không hợp lệ.");
    int index = 0;
    BigDecimal threshold = BigDecimal.ZERO;
    for (var step : steps.stream().sorted(Comparator.comparingInt(Definition::step)).toList()) {
      BusinessException.require(
          step.step() == ++index
              && step.role() != null
              && (vn.synera.iam.Permissions.ROLES.contains(step.role())
                  || vn.synera.iam.Permissions.ALL.contains(step.role()))
              && !Set.of("SCHEDULER", "STORE", "DRIVER").contains(step.role())
              && step.minAmount() != null
              && step.minAmount().compareTo(threshold) >= 0
              && step.slaHours() >= 1
              && step.slaHours() <= 720,
          "Bước, vai trò, ngưỡng hoặc SLA không hợp lệ.");
      threshold = step.minAmount();
    }
    BusinessException.require(
        steps.stream().filter(s -> s.step() == 1).findFirst().orElseThrow().minAmount().signum()
            == 0,
        "Bước đầu phải áp dụng từ giá trị 0.");
    definitions.deleteAll(definitions.findAll((r, q, cb) -> cb.equal(r.get("entityType"), type)));
    definitions.flush();
    for (var step : steps) {
      var d = new WorkflowDefinition();
      d.entityType = type;
      d.stepNo = step.step();
      d.approverRole = step.role();
      d.minAmount = step.minAmount();
      d.slaHours = step.slaHours();
      definitions.save(d);
    }
    audit.add(
        actors.current().id(),
        "WorkflowDefinition",
        0L,
        "CONFIGURE",
        Map.of("type", type, "steps", steps, "reason", reason));
    return definitions();
  }
}
