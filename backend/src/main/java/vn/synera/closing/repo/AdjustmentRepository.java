package vn.synera.closing.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.closing.domain.Adjustment;

public interface AdjustmentRepository
    extends JpaRepository<Adjustment, Long>, JpaSpecificationExecutor<Adjustment> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Adjustment e where e.id=:id")
  java.util.Optional<Adjustment> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Adjustment> findByNppId(Long nppId);
}
