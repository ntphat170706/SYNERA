package vn.synera.ticket;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.notify.EventFacade;
import vn.synera.ticket.domain.*;
import vn.synera.ticket.repo.*;

@Service
@Transactional
public class TicketFacade {
  private final TicketEntityRepository tickets;
  private final TicketEventRepository history;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final EventFacade events;
  private final AuditService audit;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final vn.synera.iam.RoleFacade roles;
  private final vn.synera.integration.StorageFacade storage;

  public TicketFacade(
      TicketEntityRepository tickets,
      TicketEventRepository history,
      CatalogFacade catalog,
      ActorFacade actors,
      EventFacade events,
      AuditService audit,
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      vn.synera.iam.RoleFacade roles,
      vn.synera.integration.StorageFacade storage) {
    this.tickets = tickets;
    this.history = history;
    this.catalog = catalog;
    this.actors = actors;
    this.events = events;
    this.audit = audit;
    this.jdbc = jdbc;
    this.roles = roles;
    this.storage = storage;
  }

  public Object create(
      Long nppId,
      Long storeId,
      Long orderId,
      Long lineId,
      String title,
      String category,
      String description) {
    catalog.npp(nppId);
    if (storeId != null)
      BusinessException.require(
          catalog.store(storeId).nppId().equals(nppId), "Cửa hàng không thuộc NPP.");
    BusinessException.require(
        title != null && !title.isBlank() && description != null && !description.isBlank(),
        "Thiếu nội dung ticket.");
    var t = new TicketEntity();
    t.nppId = nppId;
    t.storeId = storeId;
    t.orderId = orderId;
    t.lineId = lineId;
    t.title = title;
    t.category = category;
    t.description = description;
    t.status = "NEW";
    t.priority = category.equals("DELIVERY_SHORT") ? "HIGH" : "NORMAL";
    t.ownerId = actors.current().id();
    var routing =
        jdbc.queryForList("select * from ticket_routing where category=? for update", category);
    if (routing.isEmpty())
      routing = jdbc.queryForList("select * from ticket_routing where category='OTHER' for update");
    var rule = routing.get(0);
    String region = catalog.npp(nppId).region();
    var candidates =
        jdbc
            .queryForList("select id from app_user where enabled=true order by id", Long.class)
            .stream()
            .filter(
                id ->
                    roles.workspaces(id).stream()
                        .anyMatch(
                            w ->
                                w.role().equals(rule.get("department_role"))
                                    && (w.region() == null || w.region().equals(region))))
            .toList();
    if (!candidates.isEmpty()) {
      long last =
          rule.get("last_owner_id") == null ? 0 : ((Number) rule.get("last_owner_id")).longValue();
      t.assignedTo =
          candidates.stream().filter(id -> id > last).findFirst().orElse(candidates.get(0));
      jdbc.update(
          "update ticket_routing set last_owner_id=?,version=version+1 where category=?",
          t.assignedTo,
          rule.get("category"));
    }
    t.dueAt = Instant.now().plusSeconds(((Number) rule.get("sla_hours")).longValue() * 3600);
    tickets.saveAndFlush(t);
    event(t, "Tạo yêu cầu");
    return dto(t);
  }

  private void event(TicketEntity t, String note) {
    var e = new TicketEvent();
    e.ticketId = t.id;
    e.actorId = actors.current().id();
    e.status = t.status;
    e.note = note;
    e.occurredAt = Instant.now();
    history.save(e);
    audit.add(e.actorId, "Ticket", t.id, t.status, Map.of("note", Objects.toString(note, "")));
  }

  public List<Map<String, Object>> list() {
    var ids = catalog.visibleNppIds();
    var a = actors.current();
    return tickets
        .findAll(
            (r, q, cb) ->
                a.sales()
                    ? cb.and(
                        r.get("nppId").in(ids),
                        cb.or(
                            cb.equal(r.get("ownerId"), a.id()),
                            cb.equal(r.get("assignedTo"), a.id())))
                    : r.get("nppId").in(ids))
        .stream()
        .map(this::dto)
        .toList();
  }

  public Object transition(Long id, String next, String reason) {
    var t = tickets.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(t.nppId);
    if (actors.current().sales()
        && !t.ownerId.equals(actors.current().id())
        && !Objects.equals(t.assignedTo, actors.current().id())) throw BusinessException.missing();
    var allowed =
        switch (t.status) {
          case "NEW" -> Set.of("ACKNOWLEDGED", "REJECTED");
          case "ACKNOWLEDGED" -> Set.of("IN_PROGRESS");
          case "IN_PROGRESS" -> Set.of("WAITING_INFO", "RESOLVED");
          case "WAITING_INFO" -> Set.of("IN_PROGRESS");
          case "RESOLVED" -> Set.of("CLOSED", "REOPENED");
          case "CLOSED" -> Set.of("REOPENED");
          case "REOPENED" -> Set.of("IN_PROGRESS");
          default -> Set.<String>of();
        };
    if (!allowed.contains(next))
      throw BusinessException.conflict("INVALID_STATE_TRANSITION", t.status + " -> " + next);

    t.status = next;
    t.resolution = reason;
    event(t, reason);
    if (next.equals("RESOLVED"))
      events.add("TicketResolved", id, t.nppId, actors.current().id(), Map.of("note", reason));
    return dto(t);
  }

  private Map<String, Object> dto(TicketEntity t) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", t.id);
    m.put("nppId", t.nppId);
    m.put("storeId", t.storeId);
    m.put("orderId", t.orderId);
    m.put("lineId", t.lineId);
    m.put("title", t.title);
    m.put("category", t.category);
    m.put("status", t.status);
    m.put("priority", t.priority);
    m.put("ownerId", t.ownerId);
    m.put("assignedTo", t.assignedTo);
    m.put("dueAt", t.dueAt);
    m.put("escalated", t.escalated);
    m.put("description", t.description);
    m.put("resolution", t.resolution);
    return m;
  }

  public boolean unresolvedOrder(Long id) {
    return tickets.findByOrderId(id).stream()
        .anyMatch(
            t ->
                t.orderChannel.equals("SELL_IN")
                    && !Set.of("RESOLVED", "CLOSED", "REJECTED").contains(t.status));
  }

  public Object salesShortage(
      Long npp, Long store, Long order, Long line, String title, String reason) {
    var result = (Map<?, ?>) create(npp, store, order, line, title, "DELIVERY_SHORT", reason);
    var ticket = tickets.findById(((Number) result.get("id")).longValue()).orElseThrow();
    ticket.orderChannel = "SELL_OUT";
    return dto(ticket);
  }

  private TicketEntity scoped(Long id) {
    var t = tickets.findById(id).orElseThrow(BusinessException::missing);
    catalog.npp(t.nppId);
    if (actors.current().sales()
        && !t.ownerId.equals(actors.current().id())
        && !Objects.equals(t.assignedTo, actors.current().id())) throw BusinessException.missing();
    return t;
  }

  public Object detail(Long id) {
    var t = scoped(id);
    var result = dto(t);
    result.put(
        "comments",
        jdbc.queryForList("select * from ticket_comment where ticket_id=? order by id", id));
    result.put(
        "history",
        history.findAll((r, q, cb) -> cb.equal(r.get("ticketId"), id)).stream()
            .sorted(Comparator.comparing(e -> e.occurredAt))
            .toList());
    return result;
  }

  public PageSlice<Map<String, Object>> storePage(Long storeId, int page, int size) {
    catalog.store(storeId);
    var actor = actors.current();
    return PageSlice.from(
        tickets
            .findAll(
                (r, q, cb) -> {
                  var scope = cb.equal(r.get("storeId"), storeId);
                  return actor.sales()
                      ? cb.and(
                          scope,
                          cb.or(
                              cb.equal(r.get("ownerId"), actor.id()),
                              cb.equal(r.get("assignedTo"), actor.id())))
                      : scope;
                },
                PageSlice.request(page, size))
            .map(this::dto));
  }

  public Object comment(Long id, String body, Long attachment) {
    var t = scoped(id);
    BusinessException.require(
        body != null && !body.isBlank() && body.length() <= 10000,
        "Nội dung trao đổi không hợp lệ.");
    if (attachment != null) storage.assertReady(attachment, t.nppId);
    jdbc.update(
        "insert into ticket_comment(ticket_id,user_id,body,attachment_id,created_at)"
            + " values(?,?,?,?,?)",
        id,
        actors.current().id(),
        body,
        attachment,
        java.sql.Timestamp.from(Instant.now()));
    audit.add(
        actors.current().id(),
        "Ticket",
        id,
        "COMMENT",
        Map.of("attachmentId", Objects.requireNonNullElse(attachment, 0L)));
    return detail(id);
  }

  public Object assign(Long id, Long user, String reason) {
    var t = tickets.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(t.nppId);
    BusinessException.require(!actors.current().sales(), "Thiếu quyền hoặc lý do phân công.");
    var region = catalog.npp(t.nppId).region();
    BusinessException.require(
        roles.workspaces(user).stream()
            .anyMatch(
                w ->
                    w.authorities().contains("TICKET_WRITE")
                        && (Objects.equals(w.nppId(), t.nppId)
                            || vn.synera.iam.Permissions.nsx(w.role())
                                && (w.region() == null || w.region().equals(region)))),
        "Người xử lý không thuộc phạm vi ticket.");
    t.assignedTo = user;
    event(t, reason);
    return dto(t);
  }
}
