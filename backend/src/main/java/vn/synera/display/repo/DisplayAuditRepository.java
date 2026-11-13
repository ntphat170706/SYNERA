package vn.synera.display.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.display.domain.DisplayAudit;

public interface DisplayAuditRepository
    extends JpaRepository<DisplayAudit, Long>, JpaSpecificationExecutor<DisplayAudit> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from DisplayAudit e where e.id=:id")
  java.util.Optional<DisplayAudit> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<DisplayAudit> findByNppId(Long nppId);
}
