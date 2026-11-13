package vn.synera.ticket.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.ticket.domain.TicketEvent;

public interface TicketEventRepository
    extends JpaRepository<TicketEvent, Long>, JpaSpecificationExecutor<TicketEvent> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from TicketEvent e where e.id=:id")
  java.util.Optional<TicketEvent> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);
}
