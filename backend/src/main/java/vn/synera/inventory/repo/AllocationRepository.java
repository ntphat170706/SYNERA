package vn.synera.inventory.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.inventory.domain.Allocation;

public interface AllocationRepository
    extends JpaRepository<Allocation, Long>, JpaSpecificationExecutor<Allocation> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Allocation e where e.id=:id")
  java.util.Optional<Allocation> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Allocation> findByOrderId(Long orderId);
}
