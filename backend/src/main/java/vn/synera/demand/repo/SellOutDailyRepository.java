package vn.synera.demand.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.demand.domain.SellOutDaily;

public interface SellOutDailyRepository
    extends JpaRepository<SellOutDaily, Long>, JpaSpecificationExecutor<SellOutDaily> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from SellOutDaily e where e.id=:id")
  java.util.Optional<SellOutDaily> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<SellOutDaily> findByNppId(Long nppId);
}
