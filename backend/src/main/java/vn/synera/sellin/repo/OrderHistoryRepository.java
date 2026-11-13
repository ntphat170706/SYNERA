package vn.synera.sellin.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.sellin.domain.OrderHistory;

public interface OrderHistoryRepository
    extends JpaRepository<OrderHistory, Long>, JpaSpecificationExecutor<OrderHistory> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from OrderHistory e where e.id=:id")
  java.util.Optional<OrderHistory> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<OrderHistory> findByOrderId(Long orderId);
}
