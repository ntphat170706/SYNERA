package vn.synera.demand.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.demand.domain.DemandPlan;

public interface DemandPlanRepository
    extends JpaRepository<DemandPlan, Long>, JpaSpecificationExecutor<DemandPlan> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from DemandPlan e where e.id=:id")
  java.util.Optional<DemandPlan> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<DemandPlan> findByNppId(Long nppId);
}
