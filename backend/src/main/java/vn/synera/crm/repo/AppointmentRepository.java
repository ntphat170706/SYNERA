package vn.synera.crm.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.crm.domain.Appointment;

public interface AppointmentRepository
    extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from Appointment e where e.id=:id")
  java.util.Optional<Appointment> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  java.util.List<Appointment> findByNppId(Long nppId);
}
