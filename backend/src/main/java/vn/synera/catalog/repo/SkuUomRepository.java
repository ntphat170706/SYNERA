package vn.synera.catalog.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.catalog.domain.SkuUom;

public interface SkuUomRepository
    extends JpaRepository<SkuUom, Long>, JpaSpecificationExecutor<SkuUom> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from SkuUom e where e.id=:id")
  java.util.Optional<SkuUom> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.Optional<SkuUom> findBySkuIdAndCode(Long skuId, String code);

  java.util.List<SkuUom> findBySkuId(Long skuId);
}
