package vn.synera.display.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.display.domain.DisplayCriteria;

public interface DisplayCriteriaRepository
    extends JpaRepository<DisplayCriteria, Long>, JpaSpecificationExecutor<DisplayCriteria> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from DisplayCriteria e where e.id=:id")
  java.util.Optional<DisplayCriteria> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
