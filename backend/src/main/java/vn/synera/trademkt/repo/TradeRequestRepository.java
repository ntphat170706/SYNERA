package vn.synera.trademkt.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.trademkt.domain.TradeRequest;

public interface TradeRequestRepository
    extends JpaRepository<TradeRequest, Long>, JpaSpecificationExecutor<TradeRequest> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from TradeRequest e where e.id=:id")
  java.util.Optional<TradeRequest> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<TradeRequest> findByNppId(Long nppId);
}
