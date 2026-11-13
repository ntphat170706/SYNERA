package vn.synera.policy.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.policy.domain.PriceItem;

public interface PriceItemRepository
    extends JpaRepository<PriceItem, Long>, JpaSpecificationExecutor<PriceItem> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from PriceItem e where e.id=:id")
  java.util.Optional<PriceItem> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<PriceItem> findByNppId(Long nppId);
}
