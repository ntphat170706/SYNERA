package vn.synera.inventory.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.inventory.domain.StockMovement;

public interface StockMovementRepository
    extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from StockMovement e where e.id=:id")
  java.util.Optional<StockMovement> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
