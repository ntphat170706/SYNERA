package vn.synera.ticket.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.ticket.domain.TicketEntity;

public interface TicketEntityRepository
    extends JpaRepository<TicketEntity, Long>, JpaSpecificationExecutor<TicketEntity> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from TicketEntity e where e.id=:id")
  java.util.Optional<TicketEntity> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<TicketEntity> findByNppId(Long nppId);

  java.util.List<TicketEntity> findByOrderId(Long orderId);
}
