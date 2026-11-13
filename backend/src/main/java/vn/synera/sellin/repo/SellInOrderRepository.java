package vn.synera.sellin.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.sellin.domain.SellInOrder;

public interface SellInOrderRepository
    extends JpaRepository<SellInOrder, Long>, JpaSpecificationExecutor<SellInOrder> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from SellInOrder e where e.id=:id")
  java.util.Optional<SellInOrder> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<SellInOrder> findByNppId(Long nppId);
}
