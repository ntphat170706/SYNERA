package vn.synera.route;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.crm.CrmFacade;
import vn.synera.iam.ActorFacade;
import vn.synera.route.domain.*;
import vn.synera.route.repo.*;

@Service
@Transactional
public class RouteFacade {
  private final SalesRouteRepository routes;
  private final RouteStopRepository stops;
  private final VisitRepository visits;
  private final HandoverRepository handovers;
  private final CatalogFacade catalog;
  private final CrmFacade crm;
  private final ActorFacade actors;
  private final AuditService audit;
  private final JdbcTemplate jdbc;
  private final double radius;
  private final vn.synera.iam.SystemParametersFacade settings;

  public RouteFacade(
      SalesRouteRepository routes,
      RouteStopRepository stops,
      VisitRepository visits,
      HandoverRepository handovers,
      CatalogFacade catalog,
      CrmFacade crm,
      ActorFacade actors,
      AuditService audit,
      JdbcTemplate jdbc,
      @Value("${synera.rules.checkin-radius-metres}") double radius,
      vn.synera.iam.SystemParametersFacade settings) {
    this.routes = routes;
    this.stops = stops;
    this.visits = visits;
    this.handovers = handovers;
    this.catalog = catalog;
    this.crm = crm;
    this.actors = actors;
    this.audit = audit;
    this.jdbc = jdbc;
    this.radius = radius;
    this.settings = settings;
  }

  public Object create(Long npp, Long rep, String name, List<Long> storeIds, int weekday) {
    catalog.npp(npp);
    actors.assertRep(rep, npp);
    BusinessException.require(
        weekday >= 1 && weekday <= 7 && !storeIds.isEmpty(), "Cần cửa hàng và thứ 1–7.");
    var r = new SalesRoute();
    r.nppId = npp;
    r.repId = rep;
    r.name = name;
    routes.saveAndFlush(r);
    int index = 0;
    for (Long id : new LinkedHashSet<>(storeIds)) {
      var s = catalog.store(id);
      BusinessException.require(s.nppId().equals(npp), "Cửa hàng khác NPP.");
      var stop = new RouteStop();
      stop.routeId = r.id;
      stop.storeId = id;
      stop.sequenceNo = ++index;
      stop.weekday = weekday;
      stops.save(stop);
      catalog.setRoute(id, r.id);
    }
    return Map.of("id", r.id, "name", name);
  }

  public List<?> list() {
    var ids = catalog.visibleNppIds();
    var actor = actors.current();
    return routes
        .findAll(
            (r, q, cb) ->
                actor.sales()
                    ? cb.and(r.get("nppId").in(ids), cb.equal(r.get("repId"), actor.id()))
                    : r.get("nppId").in(ids))
        .stream()
        .map(r -> Map.of("id", r.id, "nppId", r.nppId, "repId", r.repId, "name", r.name))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> networkSchedule() {
    var visible = catalog.visibleNppIds();
    var actor = actors.current();
    return routes
        .findAll(
            (r, q, cb) ->
                actor.sales()
                    ? cb.and(r.get("nppId").in(visible), cb.equal(r.get("repId"), actor.id()))
                    : r.get("nppId").in(visible))
        .stream()
        .map(
            route -> {
              var rows =
                  stops.findAll((r, q, cb) -> cb.equal(r.get("routeId"), route.id)).stream()
                      .sorted(Comparator.comparing(s -> s.sequenceNo))
                      .map(
                          s ->
                              Map.<String, Object>of(
                                  "storeId",
                                  s.storeId,
                                  "sequence",
                                  s.sequenceNo,
                                  "weekday",
                                  s.weekday))
                      .toList();
              return Map.<String, Object>of(
                  "id",
                  route.id,
                  "name",
                  route.name,
                  "nppId",
                  route.nppId,
                  "repId",
                  route.repId,
                  "version",
                  route.version,
                  "stops",
                  rows);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> networkVisits(LocalDate from, LocalDate to) {
    BusinessException.require(
        from != null && to != null && !to.isBefore(from) && !to.isAfter(from.plusDays(366)),
        "Khoảng thời gian tối đa 366 ngày.");
    var visible = catalog.visibleNppIds();
    var actor = actors.current();
    return visits
        .findAll(
            (r, q, cb) ->
                cb.and(
                    r.get("nppId").in(visible),
                    cb.between(r.get("day"), from, to),
                    actor.sales() ? cb.equal(r.get("repId"), actor.id()) : cb.conjunction()))
        .stream()
        .map(
            v ->
                Map.<String, Object>of(
                    "id",
                    v.id,
                    "nppId",
                    v.nppId,
                    "storeId",
                    v.storeId,
                    "repId",
                    v.repId,
                    "day",
                    v.day,
                    "status",
                    v.status,
                    "checkedInAt",
                    Objects.toString(v.checkedInAt, ""),
                    "checkedOutAt",
                    Objects.toString(v.checkedOutAt, ""),
                    "outsideRadius",
                    v.outsideRadius))
        .toList();
  }

  public Object plan(Long routeId, LocalDate day) {
    var r = routes.lockById(routeId).orElseThrow(BusinessException::missing);
    catalog.npp(r.nppId);
    int count = planInternal(r, day);
    return Map.of("created", count, "day", day);
  }

  private int planInternal(SalesRoute route, LocalDate day) {
    if (jdbc.queryForObject(
            "select count(*) from business_holiday where holiday_date=?",
            Integer.class,
            java.sql.Date.valueOf(day))
        > 0) return 0;
    int count = 0;
    for (var stop : stops.findAll((r, q, cb) -> cb.equal(r.get("routeId"), route.id))) {
      if (stop.weekday != day.getDayOfWeek().getValue()) continue;
      boolean exists =
          visits.exists(
              (r, q, cb) ->
                  cb.and(
                      cb.equal(r.get("storeId"), stop.storeId),
                      cb.equal(r.get("repId"), route.repId),
                      cb.equal(r.get("day"), day)));
      if (!exists) {
        var v = new Visit();
        v.nppId = route.nppId;
        v.repId = route.repId;
        v.storeId = stop.storeId;
        v.day = day;
        v.status = "PLANNED";
        visits.save(v);
        count++;
      }
    }
    return count;
  }

  public int planTomorrowSystem() {
    int count = 0;
    for (var r : routes.findAll()) count += planInternal(r, Dates.today().plusDays(1));
    return count;
  }

  public List<?> today() {
    var ids = catalog.visibleNppIds();
    var a = actors.current();
    return visits
        .findAll(
            (r, q, cb) -> {
              var p = cb.and(r.get("nppId").in(ids), cb.equal(r.get("day"), Dates.today()));
              return a.sales() ? cb.and(p, cb.equal(r.get("repId"), a.id())) : p;
            })
        .stream()
        .map(this::dto)
        .toList();
  }

  public Object checkIn(Long id, double lat, double lng, String reason) {
    var v = visits.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(v.nppId);
    BusinessException.require(
        v.repId.equals(actors.current().id()), "Chỉ nhân viên được giao tự check-in.");
    BusinessException.require(
        v.status.equals("PLANNED") && v.day.equals(Dates.today()),
        "Lượt ghé không ở trạng thái/hôm nay phù hợp.");
    BusinessException.require(
        lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180, "Tọa độ không hợp lệ.");
    var s = catalog.store(v.storeId);
    BusinessException.require(
        s.latitude() != null && s.longitude() != null, "Cửa hàng chưa có tọa độ xác minh.");
    double distance = CatalogFacade.distance(lat, lng, s.latitude(), s.longitude());
    double configuredRadius =
        settings
            .decimal("checkin-radius-metres", java.math.BigDecimal.valueOf(radius))
            .doubleValue();
    boolean outside = distance > configuredRadius;

    v.latitude = lat;
    v.longitude = lng;
    v.distanceMetres = distance;
    v.outsideRadius = outside;
    v.reason = reason;
    v.checkedInAt = Instant.now();
    v.status = "CHECKED_IN";
    audit.add(
        actors.current().id(),
        "Visit",
        id,
        "CHECK_IN",
        Map.of("distance", distance, "outside", outside));
    return dto(v);
  }

  public Object checkOut(Long id, String note) {
    var v = visits.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(v.nppId);
    BusinessException.require(
        v.repId.equals(actors.current().id()) && v.status.equals("CHECKED_IN"),
        "Không thể check-out lượt ghé.");
    v.checkedOutAt = Instant.now();
    v.status = "COMPLETED";
    crm.interaction(v.storeId, "VISIT", note);
    return dto(v);
  }

  public Object handover(Long npp, Long from, Long to, String reason) {
    catalog.npp(npp);
    actors.assertRep(from, npp);
    actors.assertRep(to, npp);
    BusinessException.require(!from.equals(to), "Cần chọn hai nhân viên khác nhau.");
    var h = new Handover();
    h.nppId = npp;
    h.fromRepId = from;
    h.toRepId = to;
    h.reason = reason;
    handovers.saveAndFlush(h);
    for (var v : visits.findByNppId(npp)) {
      if (v.repId.equals(from) && v.status.equals("PLANNED")) v.repId = to;
    }
    for (var r : routes.findByNppId(npp)) if (r.repId.equals(from)) r.repId = to;
    var stores = catalog.transferRep(npp, from, to);
    int appointments = crm.handover(npp, from, to);
    jdbc.update(
        "update task set assignee_id=?,version=version+1,updated_at=? where npp_id=? and"
            + " assignee_id=? and status='OPEN'",
        to,
        java.sql.Timestamp.from(Instant.now()),
        npp,
        from);
    audit.add(
        actors.current().id(),
        "Handover",
        h.id,
        "TRANSFER",
        Map.of("from", from, "to", to, "reason", reason));
    return Map.of("id", h.id, "stores", stores, "appointments", appointments);
  }

  public int ownVisitCount(LocalDate day) {
    var id = actors.current().id();
    return (int)
        visits.count(
            (r, q, cb) ->
                cb.and(
                    cb.equal(r.get("repId"), id),
                    r.get("nppId").in(catalog.visibleNppIds()),
                    cb.equal(r.get("day"), day),
                    r.get("status").in(Set.of("CHECKED_IN", "COMPLETED"))));
  }

  private Map<String, Object> dto(Visit v) {
    return Map.of(
        "id",
        v.id,
        "storeId",
        v.storeId,
        "repId",
        v.repId,
        "day",
        v.day,
        "status",
        v.status,
        "outsideRadius",
        v.outsideRadius,
        "distanceMetres",
        Objects.requireNonNullElse(v.distanceMetres, 0D),
        "checkedInAt",
        Objects.toString(v.checkedInAt, ""));
  }
}
