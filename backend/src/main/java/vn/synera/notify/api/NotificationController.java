package vn.synera.notify.api;

import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.notify.repo.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
  private final NotificationRepository notifications;
  private final TaskEntityRepository tasks;
  private final ActorFacade actors;
  private final CatalogFacade catalog;
  private final vn.synera.notify.WorkflowFacade workflows;

  public NotificationController(
      NotificationRepository notifications,
      TaskEntityRepository tasks,
      ActorFacade actors,
      CatalogFacade catalog,
      vn.synera.notify.WorkflowFacade workflows) {
    this.notifications = notifications;
    this.tasks = tasks;
    this.actors = actors;
    this.catalog = catalog;
    this.workflows = workflows;
  }

  @GetMapping("/approvals")
  public ApiResponse<?> approvals() {
    return ApiResponse.ok(workflows.pending());
  }

  @GetMapping("/notifications")
  public ApiResponse<?> notifications() {
    var actor = actors.current();
    return ApiResponse.ok(
        notifications
            .findAll(
                (r, q, cb) ->
                    cb.and(
                        cb.equal(r.get("userId"), actor.id()),
                        cb.or(
                            r.get("nppId").in(catalog.visibleNppIds()), cb.isNull(r.get("nppId")))))
            .stream()
            .map(
                n ->
                    Map.of(
                        "id",
                        n.id,
                        "type",
                        n.type,
                        "payload",
                        n.payload,
                        "read",
                        n.readAt != null,
                        "createdAt",
                        n.createdAt))
            .toList());
  }

  @PostMapping("/notifications/{id}/read")
  @Transactional
  public ApiResponse<?> read(@PathVariable Long id) {
    var n = notifications.lockById(id).orElseThrow(BusinessException::missing);
    if (!n.userId.equals(actors.current().id())) throw BusinessException.missing();
    if (n.readAt == null) n.readAt = Instant.now();
    return ApiResponse.ok(Map.of("read", true));
  }

  @GetMapping("/tasks")
  public ApiResponse<?> tasks() {
    var actor = actors.current();
    var ids = catalog.visibleNppIds();
    return ApiResponse.ok(
        tasks
            .findAll(
                (r, q, cb) ->
                    actor.sales()
                        ? cb.and(r.get("nppId").in(ids), cb.equal(r.get("assigneeId"), actor.id()))
                        : r.get("nppId").in(ids))
            .stream()
            .map(
                t ->
                    Map.of(
                        "id",
                        t.id,
                        "assigneeId",
                        t.assigneeId,
                        "title",
                        t.title,
                        "dueAt",
                        t.dueAt,
                        "status",
                        t.status,
                        "referenceType",
                        t.referenceType,
                        "referenceId",
                        t.referenceId))
            .toList());
  }

  @PostMapping("/tasks/{id}/complete")
  @Transactional
  public ApiResponse<?> complete(@PathVariable Long id) {
    var t = tasks.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(t.nppId);
    actors.assertOwnRep(t.assigneeId);
    BusinessException.require(t.status.equals("OPEN"), "Công việc không mở.");
    BusinessException.require(
        !t.referenceType.equals("SALES_REPLACEMENT"),
        "Ghi kết quả báo khách trên chứng từ giao bù để hoàn tất việc này.");
    t.status = "DONE";
    return ApiResponse.ok(Map.of("completed", true));
  }
}
