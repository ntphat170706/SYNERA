package vn.synera.notify;

import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.notify.domain.TaskEntity;
import vn.synera.notify.repo.TaskEntityRepository;

@Service
@Transactional
public class TaskFacade {
  private final TaskEntityRepository tasks;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public TaskFacade(
      TaskEntityRepository tasks,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.tasks = tasks;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  public Long create(
      Long npp,
      Long store,
      Long rep,
      String title,
      Instant due,
      String referenceType,
      Long referenceId) {
    catalog.npp(npp);
    actors.assertRep(rep, npp);
    if (store != null)
      BusinessException.require(catalog.store(store).nppId().equals(npp), "Cửa hàng khác NPP.");
    BusinessException.require(
        title != null && !title.isBlank() && due != null,
        "Công việc thiếu nội dung hoặc hạn xử lý.");
    var task = new TaskEntity();
    task.nppId = npp;
    task.storeId = store;
    task.assigneeId = rep;
    task.title = title;
    task.dueAt = due;
    task.status = "OPEN";
    task.referenceType = referenceType;
    task.referenceId = referenceId;
    tasks.saveAndFlush(task);
    audit.add(
        actors.current().id(),
        "Task",
        task.id,
        "CREATE",
        Map.of("assignee", rep, "reference", referenceType));
    return task.id;
  }

  public boolean complete(Long id) {
    return tasks.findById(id).map(t -> t.status.equals("DONE")).orElse(false);
  }

  /** Internal domain reconciliation, never exposed as a user command. */
  public java.util.List<Long> pendingDeliveryShortOrders() {
    return jdbc.queryForList(
        "select distinct reference_id from task where status='OPEN' and reference_type in"
            + " ('DELIVERY_SHORT','SALES_DELIVERY_SHORT') and reference_id is not null",
        Long.class);
  }

  /** Internal domain reconciliation, never exposed as a user command. */
  public void resolveSalesDeliveryShort(Long order, Long npp) {
    for (var task :
        tasks.findAll(
            (r, q, cb) ->
                cb.and(
                    r.get("referenceType").in("DELIVERY_SHORT", "SALES_DELIVERY_SHORT"),
                    cb.equal(r.get("referenceId"), order),
                    cb.equal(r.get("nppId"), npp),
                    cb.equal(r.get("status"), "OPEN")))) {
      // Legacy sell-in/out task references share numeric IDs. Only a notification
      // from the same worker insertion with the sell-out payload identifies this task.
      if (task.referenceType.equals("DELIVERY_SHORT")
          && (jdbc.queryForList(
                      "select n.id from notification n join task t on n.created_at=t.created_at"
                          + " and n.user_id=t.assignee_id and n.npp_id=t.npp_id where t.id=?"
                          + " and n.type='DeliveryShort' and n.payload like '%\"storeId\":%'",
                      task.id)
                  .isEmpty()
              || !jdbc.queryForList(
                      "select n.id from notification n join task t on n.created_at=t.created_at"
                          + " and n.user_id=t.assignee_id and n.npp_id=t.npp_id where t.id=?"
                          + " and n.type='DeliveryShort' and n.payload not like '%\"storeId\":%'",
                      task.id)
                  .isEmpty())) continue;
      task.status = "DONE";
      audit.add(
          null,
          "Task",
          task.id,
          "AUTO_RESOLVED",
          Map.of(
              "referenceType",
              "DELIVERY_SHORT",
              "referenceId",
              order,
              "reason",
              "Goods received and customer follow-up recorded"));
    }
  }

  public void completeReference(String type, Long reference, Long npp, Long rep) {
    catalog.npp(npp);
    if (!actors.current().nsx() && !actors.current().id().equals(rep))
      throw new BusinessException(
          "FORBIDDEN_SCOPE", "Không có quyền hoàn tất việc của người khác.", 403);
    for (var task :
        tasks.findAll(
            (r, q, cb) ->
                cb.and(
                    cb.equal(r.get("referenceType"), type),
                    cb.equal(r.get("referenceId"), reference),
                    cb.equal(r.get("nppId"), npp),
                    cb.equal(r.get("assigneeId"), rep)))) {
      if (task.status.equals("OPEN")) {
        task.status = "DONE";
        audit.add(
            actors.current().id(),
            "Task",
            task.id,
            "DONE",
            Map.of("referenceType", type, "referenceId", reference));
      }
    }
  }
}
