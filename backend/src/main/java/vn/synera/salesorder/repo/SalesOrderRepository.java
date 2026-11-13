package vn.synera.salesorder.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.salesorder.domain.SalesOrder;

public interface SalesOrderRepository
    extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from SalesOrder e where e.id=:id")
  java.util.Optional<SalesOrder> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<SalesOrder> findByNppId(Long nppId);

  java.util.Optional<SalesOrder> findByRepIdAndIdempotencyKey(Long repId, String idempotencyKey);

  java.util.Optional<SalesOrder> findByRepIdAndClientId(Long repId, String clientId);
}
