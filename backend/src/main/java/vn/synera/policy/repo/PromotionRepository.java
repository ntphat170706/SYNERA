package vn.synera.policy.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.policy.domain.Promotion;

public interface PromotionRepository
    extends JpaRepository<Promotion, Long>, JpaSpecificationExecutor<Promotion> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Promotion e where e.id=:id")
  java.util.Optional<Promotion> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Promotion> findByNppId(Long nppId);
}
