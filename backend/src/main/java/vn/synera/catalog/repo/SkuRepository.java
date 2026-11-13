package vn.synera.catalog.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.catalog.domain.Sku;

public interface SkuRepository extends JpaRepository<Sku, Long>, JpaSpecificationExecutor<Sku> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Sku e where e.id=:id")
  java.util.Optional<Sku> lockById(@org.springframework.data.repository.query.Param("id") Long id);

  java.util.Optional<Sku> findByCode(String code);
}
