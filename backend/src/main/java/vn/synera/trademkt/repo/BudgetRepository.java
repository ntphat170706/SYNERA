package vn.synera.trademkt.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.trademkt.domain.Budget;

public interface BudgetRepository
    extends JpaRepository<Budget, Long>, JpaSpecificationExecutor<Budget> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Budget e where e.id=:id")
  java.util.Optional<Budget> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
