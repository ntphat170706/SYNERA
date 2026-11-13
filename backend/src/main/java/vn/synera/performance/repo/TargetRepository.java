package vn.synera.performance.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.performance.domain.Target;

public interface TargetRepository
    extends JpaRepository<Target, Long>, JpaSpecificationExecutor<Target> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Target e where e.id=:id")
  java.util.Optional<Target> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Target> findByNppId(Long nppId);
}
