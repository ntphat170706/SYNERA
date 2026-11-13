package vn.synera.catalog.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.catalog.domain.Warehouse;

public interface WarehouseRepository
    extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Warehouse e where e.id=:id")
  java.util.Optional<Warehouse> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Warehouse> findByOrgId(Long orgId);
}
