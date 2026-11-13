package vn.synera.performance;

import java.math.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.ClosingService;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;
import vn.synera.performance.repo.TargetRepository;

@Service
@Transactional
public class RewardFacade {
  private final TargetRepository targets;
  private final CatalogFacade catalog;
  private final ClosingService closing;
  private final SqlRecords sql;
  private final JdbcTemplate jdbc;
  private final ActorFacade actors;
  private final AuditService audit;

  public RewardFacade(
      TargetRepository targets,
      CatalogFacade catalog,
      ClosingService closing,
      SqlRecords sql,
      JdbcTemplate jdbc,
      ActorFacade actors,
      AuditService audit) {
    this.targets = targets;
    this.catalog = catalog;
    this.closing = closing;
    this.sql = sql;
    this.jdbc = jdbc;
    this.actors = actors;
    this.audit = audit;
  }

  public Object pay(Long id, String reference) {
    actors.require("CREDIT_WRITE");
    var target = targets.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(target.nppId);
    BusinessException.require(
        !Objects.equals(target.createdBy, actors.current().id())
            && reference != null
            && !reference.isBlank()
            && reference.length() <= 100,
        "Người chi thưởng phải khác người đặt chỉ tiêu và cần số chứng từ.");
    var snapshot =
        closing.snapshots(target.periodId).stream()
            .filter(s -> target.nppId.equals(s.get("nppId")))
            .findFirst()
            .orElseThrow(() -> BusinessException.invalid("Chỉ chi thưởng từ kỳ đã chốt."));
    BigDecimal amount = (BigDecimal) snapshot.get("amount");
    BusinessException.require(amount.compareTo(target.amount) >= 0, "Chưa đạt chỉ tiêu đã chốt.");
    var reward =
        amount.multiply(target.rewardPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    BusinessException.require(reward.signum() > 0, "Không có thưởng đủ điều kiện.");
    BusinessException.require(
        jdbc.queryForList("select id from reward_payout where target_id=?", id).isEmpty(),
        "Chỉ tiêu đã chi thưởng.");
    long payout =
        sql.insert(
            "reward_payout",
            SqlRecords.mutable(
                "target_id",
                id,
                "npp_id",
                target.nppId,
                "amount",
                reward,
                "reference_no",
                reference,
                "approved_by",
                actors.current().id(),
                "created_at",
                Timestamp.from(Instant.now())));
    audit.add(
        actors.current().id(),
        "RewardPayout",
        payout,
        "POST",
        Map.of("targetId", id, "amount", reward, "reference", reference));
    return Map.of("id", payout, "amount", reward);
  }

  public Object list() {
    var result = new ArrayList<Map<String, Object>>();
    for (Long npp : catalog.visibleNppIds())
      result.addAll(
          jdbc.queryForList("select * from reward_payout where npp_id=? order by id desc", npp));
    return result;
  }
}
