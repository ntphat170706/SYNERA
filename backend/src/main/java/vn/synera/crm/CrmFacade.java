package vn.synera.crm;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.crm.domain.*;
import vn.synera.crm.repo.*;
import vn.synera.iam.ActorFacade;
import vn.synera.notify.EventFacade;

@Service
@Transactional
public class CrmFacade {
  private final LeadRepository leads;
  private final AppointmentRepository appointments;
  private final InteractionRepository interactions;
  private final CampaignRepository campaigns;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final EventFacade events;
  private final AuditService audit;

  public CrmFacade(
      LeadRepository leads,
      AppointmentRepository appointments,
      InteractionRepository interactions,
      CampaignRepository campaigns,
      CatalogFacade catalog,
      ActorFacade actors,
      EventFacade events,
      AuditService audit) {
    this.leads = leads;
    this.appointments = appointments;
    this.interactions = interactions;
    this.campaigns = campaigns;
    this.catalog = catalog;
    this.actors = actors;
    this.events = events;
    this.audit = audit;
  }

  public Object lead(
      Long npp, Long rep, String name, String phone, String address, Double lat, Double lng) {
    catalog.npp(npp);
    actors.assertRep(rep, npp);
    actors.assertOwnRep(rep);
    var l = new Lead();
    l.nppId = npp;
    l.repId = rep;
    l.name = name;
    l.phone = CatalogFacade.normalizePhone(phone);
    l.address = address;
    l.latitude = lat;
    l.longitude = lng;
    l.status = "NOT_CONTACTED";
    leads.saveAndFlush(l);
    return leadDto(l);
  }

  public List<?> leads() {
    var ids = catalog.visibleNppIds();
    var a = actors.current();
    return leads
        .findAll(
            (r, q, cb) ->
                a.sales()
                    ? cb.and(r.get("nppId").in(ids), cb.equal(r.get("repId"), a.id()))
                    : r.get("nppId").in(ids))
        .stream()
        .map(this::leadDto)
        .toList();
  }

  private Map<String, Object> leadDto(Lead l) {
    return Map.of(
        "id",
        l.id,
        "nppId",
        l.nppId,
        "repId",
        l.repId,
        "name",
        l.name,
        "phone",
        l.phone,
        "address",
        l.address,
        "status",
        l.status,
        "storeId",
        Objects.requireNonNullElse(l.storeId, 0L));
  }

  public Object moveLead(Long id, String next, String reason) {
    var l = leads.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(l.nppId);
    actors.assertOwnRep(l.repId);
    var allowed =
        switch (l.status) {
          case "NOT_CONTACTED" -> Set.of("CONTACTED", "LOST");
          case "CONTACTED" -> Set.of("CONSULTING", "LOST");
          case "CONSULTING" -> Set.of("WON", "LOST");
          case "LOST" -> Set.of("CONSULTING");
          default -> Set.<String>of();
        };
    if (!allowed.contains(next))
      throw BusinessException.conflict("INVALID_STATE_TRANSITION", l.status + " -> " + next);

    if (next.equals("WON")) {
      var store =
          catalog.createStore(
              l.nppId, l.repId, l.name, l.name, l.phone, l.address, l.latitude, l.longitude);
      l.storeId = store.id();
      events.add("LeadConverted", id, l.nppId, l.repId, Map.of("storeId", l.storeId));
    }
    l.status = next;
    l.reason = reason;
    audit.add(
        actors.current().id(), "Lead", id, next, Map.of("reason", Objects.toString(reason, "")));
    return leadDto(l);
  }

  public Object appointment(Long storeId, String title, Instant due) {
    var store = catalog.store(storeId);
    BusinessException.require(due.isAfter(Instant.now()), "Lịch hẹn phải ở tương lai.");
    var a = new Appointment();
    a.nppId = store.nppId();
    a.storeId = storeId;
    a.repId = store.repId();
    a.title = title;
    a.dueAt = due;
    a.remindAt = due.minusSeconds(1800);
    a.status = "OPEN";
    appointments.saveAndFlush(a);
    return appointmentDto(a);
  }

  public List<?> appointments(Long storeId) {
    if (storeId != null) catalog.store(storeId);
    var ids = catalog.visibleNppIds();
    var a = actors.current();
    return appointments
        .findAll(
            (r, q, cb) -> {
              var p = r.get("nppId").in(ids);
              if (storeId != null) p = cb.and(p, cb.equal(r.get("storeId"), storeId));
              if (a.sales()) p = cb.and(p, cb.equal(r.get("repId"), a.id()));
              return p;
            })
        .stream()
        .map(this::appointmentDto)
        .toList();
  }

  private Map<String, Object> appointmentDto(Appointment a) {
    return Map.of(
        "id",
        a.id,
        "storeId",
        a.storeId,
        "repId",
        a.repId,
        "title",
        a.title,
        "dueAt",
        a.dueAt,
        "status",
        a.status,
        "reminded",
        a.reminded);
  }

  public Object finishAppointment(Long id, String note) {
    var a = appointments.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(a.nppId);
    actors.assertOwnRep(a.repId);
    BusinessException.require(a.status.equals("OPEN"), "Lịch hẹn đã đóng.");
    a.status = "DONE";
    interaction(a.storeId, "APPOINTMENT_RESULT", note);
    return appointmentDto(a);
  }

  public Object interaction(Long storeId, String kind, String note) {
    var s = catalog.store(storeId);
    BusinessException.require(note != null && !note.isBlank(), "Cần ghi chú tương tác.");
    var i = new Interaction();
    i.nppId = s.nppId();
    i.storeId = storeId;
    i.repId = actors.current().id();
    i.kind = kind;
    i.note = note;
    i.occurredAt = Instant.now();
    interactions.saveAndFlush(i);
    return Map.of("id", i.id, "kind", kind, "note", note, "at", i.occurredAt);
  }

  public List<?> interactions(Long storeId) {
    catalog.store(storeId);
    return interactions.findAll((r, q, cb) -> cb.equal(r.get("storeId"), storeId)).stream()
        .map(
            i ->
                Map.of(
                    "id",
                    i.id,
                    "kind",
                    i.kind,
                    "note",
                    i.note,
                    "at",
                    i.occurredAt,
                    "repId",
                    i.repId))
        .toList();
  }

  public PageSlice<?> storePage(Long storeId, String section, int page, int size) {
    catalog.store(storeId);
    var request = PageSlice.request(page, size);
    if (section.equals("appointments")) {
      var actor = actors.current();
      return PageSlice.from(
          appointments
              .findAll(
                  (r, q, cb) -> {
                    var scope = cb.equal(r.get("storeId"), storeId);
                    return actor.sales()
                        ? cb.and(scope, cb.equal(r.get("repId"), actor.id()))
                        : scope;
                  },
                  request)
              .map(this::appointmentDto));
    }
    BusinessException.require(section.equals("interactions"), "Phần hồ sơ không hợp lệ.");
    return PageSlice.from(
        interactions
            .findAll((r, q, cb) -> cb.equal(r.get("storeId"), storeId), request)
            .map(
                i ->
                    Map.<String, Object>of(
                        "id",
                        i.id,
                        "kind",
                        i.kind,
                        "note",
                        i.note,
                        "at",
                        i.occurredAt,
                        "repId",
                        i.repId)));
  }

  public int handover(Long npp, Long from, Long to) {
    catalog.npp(npp);
    int count = 0;
    for (var a : appointments.findByNppId(npp)) {
      if (a.repId.equals(from) && a.status.equals("OPEN")) {
        a.repId = to;
        count++;
      }
    }
    return count;
  }

  public Object campaign(Long npp, String name, String group, String message) {
    catalog.npp(npp);
    var c = new Campaign();
    c.nppId = npp;
    c.name = name;
    c.groupName = group;
    c.message = message;
    c.status = "DRAFT";
    campaigns.saveAndFlush(c);
    return Map.of("id", c.id, "status", c.status);
  }
}
