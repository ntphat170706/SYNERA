package vn.synera.route.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.route.domain.Visit;

public interface VisitRepository
    extends JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Visit e where e.id=:id")
  java.util.Optional<Visit> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Visit> findByNppId(Long nppId);
}
