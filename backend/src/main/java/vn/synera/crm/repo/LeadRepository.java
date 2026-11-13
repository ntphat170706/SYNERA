package vn.synera.crm.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.crm.domain.Lead;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Lead e where e.id=:id")
  java.util.Optional<Lead> lockById(@org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Lead> findByNppId(Long nppId);
}
