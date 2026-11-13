package vn.synera.closing.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.closing.domain.PeriodSnapshot;

public interface PeriodSnapshotRepository
    extends JpaRepository<PeriodSnapshot, Long>, JpaSpecificationExecutor<PeriodSnapshot> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from PeriodSnapshot e where e.id=:id")
  java.util.Optional<PeriodSnapshot> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<PeriodSnapshot> findByNppId(Long nppId);
}
