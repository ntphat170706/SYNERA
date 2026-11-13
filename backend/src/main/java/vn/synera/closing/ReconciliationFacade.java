package vn.synera.closing;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.synera.catalog.CatalogFacade;
import vn.synera.closing.domain.ReconciliationItem;
import vn.synera.closing.repo.ReconciliationItemRepository;
import vn.synera.common.*;
import vn.synera.iam.ActorFacade;

@Service
@Transactional
public class ReconciliationFacade {
  private final ReconciliationItemRepository items;
  private final CatalogFacade catalog;
  private final ActorFacade actors;
  private final AuditService audit;

  public ReconciliationFacade(
      ReconciliationItemRepository items,
      CatalogFacade catalog,
      ActorFacade actors,
      AuditService audit) {
    this.items = items;
    this.catalog = catalog;
    this.actors = actors;
    this.audit = audit;
  }

  public void shortage(Long npp, Long order, Long line, String description) {
    var i = new ReconciliationItem();
    i.nppId = npp;
    i.orderId = order;
    i.lineId = line;
    i.kind = "RECEIPT_DIFFERENCE";
    i.description = description;
    i.status = "OPEN";
    i.ownerId = actors.current().id();
    items.save(i);
  }

  public boolean unresolved(Long order) {
    return items.findByOrderId(order).stream().anyMatch(i -> !i.status.equals("RESOLVED"));
  }

  public List<?> list() {
    var ids = catalog.visibleNppIds();
    return items.findAll((r, q, cb) -> r.get("nppId").in(ids)).stream()
        .map(
            i ->
                Map.of(
                    "id",
                    i.id,
                    "orderId",
                    i.orderId,
                    "lineId",
                    Objects.requireNonNullElse(i.lineId, 0L),
                    "status",
                    i.status,
                    "description",
                    i.description,
                    "ownerId",
                    i.ownerId))
        .toList();
  }

  public Object resolve(Long id, String reason) {
    var i = items.lockById(id).orElseThrow(BusinessException::missing);
    catalog.npp(i.nppId);

    i.status = "RESOLVED";
    audit.add(actors.current().id(), "Reconciliation", id, "RESOLVE", Map.of("reason", reason));
    return Map.of("id", id, "status", i.status);
  }
}
