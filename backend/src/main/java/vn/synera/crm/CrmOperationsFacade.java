package vn.synera.crm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.crm.domain.Campaign;
import vn.synera.crm.repo.CampaignRepository;
import vn.synera.iam.ActorFacade;
import vn.synera.notify.TaskFacade;
import vn.synera.policy.PricingFacade;
import vn.synera.salesorder.SalesFacade;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CrmOperationsFacade {
  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;
  private final Jsons json;
  private final ObjectMapper mapper;
  private final PricingFacade pricing;
  private final SalesFacade sales;
  private final TaskFacade tasks;
  private final CampaignRepository campaigns;

  public CrmOperationsFacade(
      JdbcTemplate jdbc,
      SqlRecords sql,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit,
      Jsons json,
      ObjectMapper mapper,
      PricingFacade pricing,
      SalesFacade sales,
      TaskFacade tasks,
      CampaignRepository campaigns) {
    this.jdbc = jdbc;
    this.sql = sql;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
    this.json = json;
    this.mapper = mapper;
    this.pricing = pricing;
    this.sales = sales;
    this.tasks = tasks;
    this.campaigns = campaigns;
  }

  private Map<String, Object> ownedQuote(long id, boolean lock) {
    var q = sql.row("quotation", id, lock);
    catalog.store(SqlRecords.id(q, "store_id"));
    actors.assertOwnRep(SqlRecords.id(q, "rep_id"));
    return q;
  }

  public List<Map<String, Object>> quotations(Long storeId) {
    var stores =
        catalog.stores("").stream()
            .filter(s -> storeId == null || s.id().equals(storeId))
            .map(CatalogFacade.Store::id)
            .toList();
    if (stores.isEmpty()) return List.of();
    return jdbc.queryForList(
        "select id,npp_id,store_id,rep_id,status,valid_until,total,sales_order_id,created_at from"
            + " quotation where store_id in ("
            + String.join(",", Collections.nCopies(stores.size(), "?"))
            + ") order by id desc limit 1000",
        stores.toArray());
  }

  public Object quotation(Long store, List<DocumentRequests.Line> lines, LocalDate validUntil) {
    var s = catalog.store(store);
    BusinessException.require(
        validUntil != null
            && !validUntil.isBefore(Dates.today())
            && !validUntil.isAfter(Dates.today().plusDays(90)),
        "Báo giá có hiệu lực tối đa 90 ngày.");
    var quote = pricing.quote(s.nppId(), s.id(), lines, Dates.today(), false);
    long id =
        sql.insert(
            "quotation",
            SqlRecords.versioned(
                "npp_id",
                s.nppId(),
                "store_id",
                store,
                "rep_id",
                actors.current().id(),
                "status",
                "DRAFT",
                "valid_until",
                java.sql.Date.valueOf(validUntil),
                "total",
                quote.total(),
                "request_json",
                json.write(lines),
                "quote_json",
                json.write(quote)));
    audit.add(actors.current().id(), "Quotation", id, "CREATE", Map.of("amount", quote.total()));
    return ownedQuote(id, false);
  }

  public Object quotationState(Long id, String state, String reason) {
    var q = ownedQuote(id, true);
    String old = (String) q.get("status");

    boolean valid =
        switch (old) {
          case "DRAFT" -> Set.of("SENT", "CANCELLED").contains(state);
          case "SENT" -> Set.of("ACCEPTED", "REJECTED", "CANCELLED").contains(state);
          default -> false;
        };
    BusinessException.require(valid, "Báo giá không thể chuyển trạng thái này.");
    Long order = null;
    if (state.equals("ACCEPTED")) {
      actors.require("SALES_CREATE");
      BusinessException.require(
          !((java.sql.Date) q.get("valid_until")).toLocalDate().isBefore(Dates.today()),
          "Báo giá đã hết hạn.");
      try {
        List<DocumentRequests.Line> lines =
            mapper.readValue((String) q.get("request_json"), new TypeReference<>() {});
        var result =
            (Map<?, ?>)
                sales.createExpected(
                    SqlRecords.id(q, "store_id"),
                    "quotation-" + id,
                    "quotation-" + id,
                    Instant.now(),
                    lines,
                    (String) q.get("quote_json"));
        order = ((Number) result.get("id")).longValue();
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException("Invalid stored quotation", e);
      }
    }
    jdbc.update(
        "update quotation set"
            + " status=?,reason=?,sales_order_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
            + " where id=?",
        state,
        reason,
        order,
        id);
    audit.add(actors.current().id(), "Quotation", id, state, Map.of("reason", reason));
    return ownedQuote(id, false);
  }

  public Object survey(
      Long store, LocalDate date, List<DocumentRequests.Quantity> lines, String note) {
    var s = catalog.store(store);
    BusinessException.require(
        date != null
            && !date.isAfter(Dates.today())
            && lines != null
            && !lines.isEmpty()
            && lines.size() <= 100,
        "Ngày hoặc dòng khảo sát không hợp lệ.");
    for (var line : lines) {
      Long sku = line.skuId();
      catalog.product(sku);
      BigDecimal qty = line.qtyBase();
      BusinessException.require(
          qty.signum() >= 0 && qty.stripTrailingZeros().scale() <= 3, "Tồn khảo sát không hợp lệ.");
    }
    long id =
        sql.insert(
            "store_stock_survey",
            SqlRecords.versioned(
                "npp_id",
                s.nppId(),
                "store_id",
                store,
                "rep_id",
                actors.current().id(),
                "survey_date",
                java.sql.Date.valueOf(date),
                "lines_json",
                json.write(lines),
                "note",
                note));
    return Map.of("id", id);
  }

  public List<Map<String, Object>> surveys(Long store) {
    catalog.store(store);
    return jdbc.queryForList(
        "select * from store_stock_survey where store_id=? order by survey_date desc limit 100",
        store);
  }

  public List<Map<String, Object>> campaigns() {
    var ids = catalog.visibleNppIds();
    return campaigns.findAll((r, q, cb) -> r.get("nppId").in(ids)).stream()
        .map(
            c ->
                Map.<String, Object>of(
                    "id",
                    c.id,
                    "nppId",
                    c.nppId,
                    "name",
                    c.name,
                    "groupName",
                    c.groupName,
                    "message",
                    c.message,
                    "status",
                    c.status))
        .toList();
  }

  public Object campaign(Long npp, String name, String group, String message) {
    catalog.npp(npp);
    BusinessException.require(
        !actors.current().sales(), "Nhân viên thị trường không tự phát hành chiến dịch toàn NPP.");
    var c = new Campaign();
    c.nppId = npp;
    c.name = name;
    c.groupName = Objects.requireNonNullElse(group, "");
    c.message = message;
    c.status = "DRAFT";
    campaigns.saveAndFlush(c);
    return Map.of("id", c.id, "status", c.status);
  }

  public Object launch(Long id, Instant due) {
    var c = campaigns.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(c.nppId);
    BusinessException.require(
        !actors.current().sales() && c.status.equals("DRAFT") && due.isAfter(Instant.now()),
        "Chiến dịch chưa sẵn sàng phát hành.");
    var stores =
        catalog.stores("").stream()
            .filter(
                s ->
                    s.nppId().equals(c.nppId)
                        && (c.groupName.isBlank() || c.groupName.equals(s.groupName())))
            .toList();
    BusinessException.require(
        !stores.isEmpty() && stores.size() <= 2000, "Chiến dịch cần 1–2000 cửa hàng phù hợp.");
    for (var store : stores) {
      Long task =
          tasks.create(
              c.nppId,
              store.id(),
              store.repId(),
              (c.name + ": " + c.message)
                  .substring(0, Math.min(250, (c.name + ": " + c.message).length())),
              due,
              "CAMPAIGN",
              c.id);
      sql.insert(
          "campaign_recipient",
          SqlRecords.versioned(
              "campaign_id",
              id,
              "npp_id",
              c.nppId,
              "store_id",
              store.id(),
              "rep_id",
              store.repId(),
              "task_id",
              task,
              "status",
              "ASSIGNED"));
    }
    c.status = "RUNNING";
    audit.add(actors.current().id(), "Campaign", id, "LAUNCH", Map.of("recipients", stores.size()));
    return Map.of("id", id, "status", c.status, "recipients", stores.size());
  }

  public Object campaignProgress(Long id) {
    var c = campaigns.findById(id).orElseThrow(BusinessException::missing);
    catalog.npp(c.nppId);
    var recipients = jdbc.queryForList("select * from campaign_recipient where campaign_id=?", id);
    long done =
        recipients.stream().filter(r -> tasks.complete(SqlRecords.id(r, "task_id"))).count();
    return Map.of(
        "id",
        id,
        "total",
        recipients.size(),
        "completed",
        done,
        "status",
        done == recipients.size() && !recipients.isEmpty() ? "COMPLETED" : c.status);
  }
}
