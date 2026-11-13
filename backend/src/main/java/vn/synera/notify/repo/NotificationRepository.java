package vn.synera.notify.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.notify.domain.Notification;

public interface NotificationRepository
    extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Notification e where e.id=:id")
  java.util.Optional<Notification> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Notification> findByNppId(Long nppId);
}
