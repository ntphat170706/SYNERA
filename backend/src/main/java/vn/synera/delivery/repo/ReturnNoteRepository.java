package vn.synera.delivery.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.delivery.domain.ReturnNote;

public interface ReturnNoteRepository
    extends JpaRepository<ReturnNote, Long>, JpaSpecificationExecutor<ReturnNote> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from ReturnNote e where e.id=:id")
  java.util.Optional<ReturnNote> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<ReturnNote> findByNppId(Long nppId);

  java.util.List<ReturnNote> findByOrderId(Long orderId);
}
