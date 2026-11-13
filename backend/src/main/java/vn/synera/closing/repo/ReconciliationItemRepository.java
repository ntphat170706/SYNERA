package vn.synera.closing.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.closing.domain.ReconciliationItem;

public interface ReconciliationItemRepository
    extends JpaRepository<ReconciliationItem, Long>, JpaSpecificationExecutor<ReconciliationItem> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select e from ReconciliationItem e where e.id=:id")
  java.util.Optional<ReconciliationItem> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<ReconciliationItem> findByNppId(Long nppId);

  java.util.List<ReconciliationItem> findByOrderId(Long orderId);
}
