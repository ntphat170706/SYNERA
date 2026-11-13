package vn.synera.route.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.route.domain.Handover;

public interface HandoverRepository
    extends JpaRepository<Handover, Long>, JpaSpecificationExecutor<Handover> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Handover e where e.id=:id")
  java.util.Optional<Handover> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Handover> findByNppId(Long nppId);
}
