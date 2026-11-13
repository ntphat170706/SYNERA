package vn.synera.closing.repo;

import org.springframework.data.jpa.repository.*;
import vn.synera.closing.domain.ReportPeriod;

public interface ReportPeriodRepository
    extends JpaRepository<ReportPeriod, Long>, JpaSpecificationExecutor<ReportPeriod> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from ReportPeriod e where e.id=:id")
  java.util.Optional<ReportPeriod> lockById(
      @org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
  @org.springframework.data.jpa.repository.Query("select e from ReportPeriod e where e.id=:id")
  java.util.Optional<ReportPeriod> readGuard(
      @org.springframework.data.repository.query.Param("id") Long id);
}
