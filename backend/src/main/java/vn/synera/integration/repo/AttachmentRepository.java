package vn.synera.integration.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.integration.domain.Attachment;

public interface AttachmentRepository
    extends JpaRepository<Attachment, Long>, JpaSpecificationExecutor<Attachment> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Attachment e where e.id=:id")
  java.util.Optional<Attachment> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Attachment> findByNppId(Long nppId);
}
