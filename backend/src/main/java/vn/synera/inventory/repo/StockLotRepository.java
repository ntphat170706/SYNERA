package vn.synera.inventory.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.inventory.domain.StockLot;

public interface StockLotRepository
    extends JpaRepository<StockLot, Long>, JpaSpecificationExecutor<StockLot> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from StockLot e where e.id=:id")
  java.util.Optional<StockLot> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
