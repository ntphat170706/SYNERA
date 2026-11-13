package vn.synera.trademkt;

import java.math.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.integration.StorageFacade;
import vn.synera.trademkt.repo.*;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class SettlementFacade {
  private final TradeRequestRepository requests;
  private final BudgetRepository budgets;
  private final JdbcTemplate jdbc;
  private final SqlRecords sql;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final StorageFacade storage;
  private final AuditService audit;

  public SettlementFacade(
      TradeRequestRepository requests,
      BudgetRepository budgets,
      JdbcTemplate jdbc,
      SqlRecords sql,
      CatalogFacade catalog,
      ActorFacade actors,
      StorageFacade storage,
      AuditService audit) {
    this.requests = requests;
    this.budgets = budgets;
    this.jdbc = jdbc;
    this.sql = sql;
    this.catalog = catalog;
    this.actors = actors;
    this.storage = storage;
    this.audit = audit;
  }

  public Object settle(
      Long id, BigDecimal amount, String reference, Long attachment, String reason) {
    actors.require("CREDIT_WRITE");
    var request = requests.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(request.nppId);
    BusinessException.require(
        Set.of("APPROVED", "PARTIALLY_APPROVED").contains(request.status)
            && amount != null
            && amount.signum() >= 0
            && amount.scale() <= 2
            && amount.compareTo(request.approved) <= 0
            && reference != null
            && !reference.isBlank()
            && reference.length() <= 100,
        "Đề xuất chưa được duyệt hoặc thực chi vượt cam kết.");
    BusinessException.require(
        jdbc.queryForList(
                "select id from approval_request where entity_type='TRADE_REQUEST' and entity_id=?"
                    + " and approved_by=?",
                id,
                actors.current().id())
            .isEmpty(),
        "Người quyết toán phải khác người đã duyệt đề xuất.");
    if (amount.signum() > 0) {
      BusinessException.require(attachment != null, "Thực chi cần chứng từ.");
      storage.assertReady(attachment, request.nppId);
    }
    var budget = budgets.lockById(request.budgetId).orElseThrow();
    budget.committed = budget.committed.subtract(request.approved).add(amount);
    jdbc.update("update budget_allocation set spent=spent+? where id=?", amount, budget.id);
    long settlement =
        sql.insert(
            "trade_settlement",
            SqlRecords.mutable(
                "request_id",
                id,
                "npp_id",
                request.nppId,
                "amount",
                amount,
                "reference_no",
                reference,
                "attachment_id",
                attachment,
                "approved_by",
                actors.current().id(),
                "reason",
                reason,
                "created_at",
                Timestamp.from(Instant.now())));
    request.status = "SETTLED";
    audit.add(
        actors.current().id(),
        "TradeSettlement",
        settlement,
        "POST",
        Map.of("amount", amount, "reference", reference));
    return Map.of("id", settlement, "status", "SETTLED", "amount", amount);
  }

  public Object list() {
    var result = new ArrayList<Map<String, Object>>();
    for (Long npp : catalog.visibleNppIds())
      result.addAll(
          jdbc.queryForList("select * from trade_settlement where npp_id=? order by id desc", npp));
    return result;
  }

  public Object budgets() {
    return budgets.findAll().stream()
        .map(
            b ->
                Map.of(
                    "id",
                    b.id,
                    "name",
                    b.name,
                    "amount",
                    b.amount,
                    "committed",
                    b.committed,
                    "available",
                    b.amount.subtract(b.committed),
                    "from",
                    b.periodStart,
                    "to",
                    b.periodEnd))
        .toList();
  }
}
