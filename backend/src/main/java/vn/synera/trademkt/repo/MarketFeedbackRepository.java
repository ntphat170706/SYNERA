package vn.synera.trademkt.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.trademkt.domain.MarketFeedback;

public interface MarketFeedbackRepository
    extends JpaRepository<MarketFeedback, Long>, JpaSpecificationExecutor<MarketFeedback> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from MarketFeedback e where e.id=:id")
  java.util.Optional<MarketFeedback> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<MarketFeedback> findByNppId(Long nppId);
}
