package vn.synera.crm.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.crm.domain.Interaction;

public interface InteractionRepository
    extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Interaction e where e.id=:id")
  java.util.Optional<Interaction> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Interaction> findByNppId(Long nppId);
}
