package vn.synera.catalog.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.catalog.domain.Organization;

public interface OrganizationRepository
    extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Organization e where e.id=:id")
  java.util.Optional<Organization> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.Optional<Organization> findByCode(String code);
}
