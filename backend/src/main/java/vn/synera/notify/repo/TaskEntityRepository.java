package vn.synera.notify.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.notify.domain.TaskEntity;

public interface TaskEntityRepository
    extends JpaRepository<TaskEntity, Long>, JpaSpecificationExecutor<TaskEntity> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from TaskEntity e where e.id=:id")
  java.util.Optional<TaskEntity> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<TaskEntity> findByNppId(Long nppId);
}
