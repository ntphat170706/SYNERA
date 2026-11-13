package vn.synera.notify.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.notify.domain.WorkflowDefinition;

public interface WorkflowDefinitionRepository
    extends JpaRepository<WorkflowDefinition, Long>, JpaSpecificationExecutor<WorkflowDefinition> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select e from WorkflowDefinition e where e.id=:id")
  java.util.Optional<WorkflowDefinition> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
